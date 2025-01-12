package org.homio.hquery;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.homio.hquery.api.*;
import org.homio.hquery.api.ListParse.BooleanLineParse;
import org.homio.hquery.api.ListParse.LineParse;
import org.homio.hquery.api.ListParse.LineParsers;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.filter.AnnotationTypeFilter;

@AllArgsConstructor
public class HQueryFactoryPostProcessor implements BeanFactoryPostProcessor {

    public static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{.*?}");
    private static final Map<String, ProcessCache> cache = new HashMap<>();

    private final String basePackages;
    private final HQueryFactoryPostHandler handler;
    private final HQueryLogger logger;

    private static String replaceValues(String text, BiFunction<String, String, String> propertyGetter) {
        Matcher matcher = HQueryFactoryPostProcessor.ENV_PATTERN.matcher(text);
        StringBuilder noteBuffer = new StringBuilder();
        while (matcher.find()) {
            String group = matcher.group();
            matcher.appendReplacement(noteBuffer, getEnvProperty(group, propertyGetter));
        }
        matcher.appendTail(noteBuffer);
        return noteBuffer.isEmpty() ? text : noteBuffer.toString();
    }

    @SneakyThrows
    private static String getEnvProperty(String value, BiFunction<String, String, String> propertyGetter) {
        String[] array = getSpringValuesPattern(value);
        return propertyGetter.apply(array[0], array[1]);
    }

    private static String[] getSpringValuesPattern(String value) {
        String valuePattern = value.substring(2, value.length() - 1);
        return valuePattern.contains(":") ? valuePattern.split(":") : new String[]{valuePattern, ""};
    }

    @SneakyThrows
    private static <T> Constructor<T> findObjectConstructor(Class<T> clazz, Class<?>... parameterTypes) {
        if (parameterTypes.length > 0) {
            return clazz.getConstructor(parameterTypes);
        }
        for (Constructor<?> constructor : clazz.getConstructors()) {
            if (constructor.getParameterCount() == 0) {
                constructor.setAccessible(true);
                return (Constructor<T>) constructor;
            }
        }
        return null;
    }

    @SneakyThrows
    private static <T> T newInstance(Class<T> clazz) {
        Constructor<T> constructor = findObjectConstructor(clazz);
        if (constructor != null) {
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
        throw new IllegalArgumentException("Unable to find default constructor for class: " + clazz);
    }

    @Override
    @SneakyThrows
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        Environment env = beanFactory.getBean(Environment.class);
        HQueryExecutor hQueryExecutor = buildHQueryExecutor();
        List<Class<?>> classes = getClassesWithAnnotation();
        for (Class<?> aClass : classes) {
            beanFactory.registerSingleton(aClass.getSimpleName(),
                    Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{aClass}, (proxy, method, args) -> {
                        if (method.getName().equals("toString")) {
                            return aClass.getSimpleName() + ":" +
                                   aClass.getDeclaredAnnotation(HardwareRepository.class).description();
                        }
                        return handleQuery(env, hQueryExecutor, aClass, proxy, method, args);
                    }));
        }

        hQueryExecutor.prepare(beanFactory, env);

        if (this.handler != null) {
            this.handler.accept(beanFactory);
        }
    }

    private Object handleQuery(Environment env, HQueryExecutor hQueryExecutor, Class<?> aClass, Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> returnType = method.getReturnType();
        List<Object> results = null;
        for (HardwareQuery hardwareQuery : method.getDeclaredAnnotationsByType(HardwareQuery.class)) {
            if (results == null) {
                results = new ArrayList<>();
            }
            results.add(handleHardwareQuery(hardwareQuery, args, method, env, aClass, hQueryExecutor));
        }
        Optional<AtomicReference<Object>> value = handleCurlQuery(method, args, env);
        if (value.isPresent()) {
            return value.get().get();
        }
        if (results != null) {
            if (results.size() == 1) {
                return results.iterator().next();
            } else if (returnType.isAssignableFrom(List.class)) {
                return results;
            } else {
                return null;
            }
        }

        if (method.isDefault()) {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle methodHandle = lookup.unreflectSpecial(method, aClass);
            return methodHandle.bindTo(proxy).invokeWithArguments(args);
        }
        throw new RuntimeException("Unable to execute hardware method without implementation");
    }

    private Optional<AtomicReference<Object>> handleCurlQuery(Method method, Object[] args, Environment env) {
        CurlQuery curlQuery = method.getDeclaredAnnotation(CurlQuery.class);
        if (curlQuery != null) {
            String argCmd = replaceStringWithArgs(curlQuery.value(), args, method);
            String command = replaceValues(argCmd, env::getProperty);
            ProcessCache processCache;

            if ((curlQuery.cache() && curlQuery.cacheValidSec() > 0)
                && cache.containsKey(command)
                && (System.currentTimeMillis() - cache.get(command).executedTime) / 1000 < cache.get(command).cacheValidInSec) {
                processCache = cache.get(command);
            } else {
                processCache = new ProcessCache(curlQuery.cacheValidSec());
                try {
                    Object result = Curl.getWithTimeout(command, method.getReturnType(), curlQuery.maxSecondsTimeout());
                    Function<Object, Object> mapping = newInstance(curlQuery.mapping());
                    processCache.response = mapping.apply(result);

                } catch (Exception ex) {
                    String msg = String.format("Error while execute curl command '%s'. Msg: '%s'%n", command, getErrorMessage(ex));
                    logger.error(msg);
                    processCache.errors.add(getErrorMessage(ex));
                    if (!curlQuery.ignoreOnError()) {
                        throw new HardwareException(processCache.errors, processCache.inputs, -1);
                    } else if (!curlQuery.valueOnError().isEmpty()) {
                        return Optional.of(new AtomicReference<>(curlQuery.valueOnError()));
                    }
                    processCache.retValue = -1;

                    // to avoid NPE instantiate empty class
                    if (method.getReturnType().isAssignableFrom(JsonNode.class)) {
                        processCache.response = new ObjectMapper().createObjectNode().put("error", getErrorMessage(ex));
                    } else if (!method.getReturnType().isAssignableFrom(String.class)) {
                        processCache.response = newInstance(method.getReturnType());
                    }
                }
                if (processCache.errors.isEmpty() && curlQuery.cache() && curlQuery.cacheValidSec() > 0) {
                    cache.put(command, processCache);
                }
            }
            return Optional.of(new AtomicReference<>(processCache.response));
        }
        return Optional.empty();
    }

    private String replaceStringWithArgs(String str, Object[] args, Method method) {
        if (args != null) {
            Annotation[][] apiParams = method.getParameterAnnotations();
            for (int i = 0; i < args.length; i++) {
                String regexp = null;
                Object arg = args[i];
                if (isParamHasAnnotation(apiParams, i, HQueryParam.class)) {
                    regexp = ((HQueryParam) apiParams[i][0]).value();
                }

                String text = "";
                while (!text.equals(str)) {
                    text = str;
                    str = str.replace(regexp == null ? ":([^\\s]+)" : ":" + regexp, String.valueOf(arg));
                }
            }
        }
        return str;
    }

    @SneakyThrows
    private Object handleHardwareQuery(HardwareQuery hardwareQuery, Object[] args, Method method, Environment env,
                                       Class<?> aClass, HQueryExecutor hQueryExecutor) {
        if(env.acceptsProfiles(Profiles.of("offline"))) {
            return null;
        }
        ErrorsHandler errorsHandler = method.getAnnotation(ErrorsHandler.class);
        int maxWaitTimeout = getMaxWaitTimeout(hardwareQuery, args, method);
        ProgressBar progressBar = getProgressBar(args, hardwareQuery.printOutput());

        List<String> parts = buildExecutableCommand(hardwareQuery, args, method, env, hQueryExecutor);
        if (parts.isEmpty()) {
            return returnOnDisableValue(method, aClass);
        }
        String[] cmdParts = parts.toArray(new String[0]);
        String command = String.join(", ", parts);
        ProcessCache processCache;
        if (hardwareQuery.cacheValid() > 0
            && cache.containsKey(command)
            && (System.currentTimeMillis() - cache.get(command).executedTime) / 1000 < cache.get(command).cacheValidInSec) {
            processCache = cache.get(command);
        } else {
            processCache = new ProcessCache(hardwareQuery.cacheValid());
            progressBar.progress(0,
                    "Execute: '%s'. Command: '%s'".formatted(hardwareQuery.name(), command));
            ProcessBuilder processBuilder;
            StreamGobbler streamGobbler = new StreamGobbler(hardwareQuery.name(), message -> {
                processCache.inputs.add(message);
                if (!message.isEmpty()) {
                    progressBar.progress(50D, message, false);
                }
            }, message -> {
                processCache.errors.add(message);
                if (!message.isEmpty()) {
                    progressBar.progress(50D, message, true);
                }
            });
            try {
                File dir = null;
                if (!StringUtils.isEmpty(hardwareQuery.dir())) {
                    dir = new File(replaceStringWithArgs(hardwareQuery.dir(), args, method));
                }
                if (cmdParts.length > 1) {
                    processBuilder = new ProcessBuilder(cmdParts);
                } else if (SystemUtils.IS_OS_WINDOWS) {
                    processBuilder = new ProcessBuilder("cmd.exe", "/C", cmdParts[0]);
                } else {
                    processBuilder = new ProcessBuilder("/bin/sh", "-c", cmdParts[0]);
                }
                processBuilder.directory(dir);

                Process process = processBuilder.start();
                streamGobbler.stream(process);

                Thread.sleep(10);
                if (!process.waitFor(maxWaitTimeout, SECONDS)) {
                    process.destroy();
                }
                processCache.retValue = process.exitValue();
            } catch (Exception ex) {
                processCache.retValue = 1;
                processCache.errors.add(getErrorMessage(ex));
            } finally {
                streamGobbler.stopStream(hardwareQuery.errorStreamWaitTimeoutMs(), 5000);
                if (processCache.errors.isEmpty() && hardwareQuery.cacheValid() > 0) {
                    cache.put(command, processCache);
                }
            }
        }

        return handleCommandResult(hardwareQuery, method, errorsHandler, command, processCache.retValue, processCache.inputs,
                processCache.errors);
    }

    private ProgressBar getProgressBar(Object[] args, boolean printOutput) {
        var progressBar = args == null || args.length == 0 ? null :
                Stream.of(args).filter(arg -> arg instanceof ProgressBar)
                        .map(arg -> (ProgressBar) arg).findAny().orElse(null);
        if (progressBar == null) {
            progressBar = (progress, message, isError) -> {
                if (printOutput) {
                    if (isError) {
                        logger.error(message);
                    } else {
                        logger.info(message);
                    }
                }
            };
        }
        return progressBar;
    }

    private List<String> buildExecutableCommand(HardwareQuery hardwareQuery, Object[] args, Method method, Environment env, HQueryExecutor hQueryExecutor) {
        List<String> parts = new ArrayList<>();
        String[] values = hQueryExecutor.getValues(hardwareQuery);
        Stream.of(values).filter(cmd -> !cmd.isEmpty()).forEach(cmd -> {
            String argCmd = replaceStringWithArgs(cmd, args, method);
            String envCmd = replaceValues(argCmd, env::getProperty);
            parts.add(hQueryExecutor.updateCommand(envCmd));
        });
        return parts;
    }

    private int getMaxWaitTimeout(HardwareQuery hardwareQuery, Object[] args, Method method) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            if (isParamHasAnnotation(parameterAnnotations, i, HQueryMaxWaitTimeout.class)) {
                return (int) args[i];
            }
        }
        return hardwareQuery.maxSecondsTimeout();
    }

    private Object returnOnDisableValue(Method method, Class<?> aClass) {
        // in case we expect return num we ignore any errors
        Class<?> returnType = method.getReturnType();
        HardwareRepository hardwareRepository = aClass.getDeclaredAnnotation(HardwareRepository.class);
        if (returnType.isPrimitive()) {
            switch (returnType.getName()) {
                case "int" -> {
                    return hardwareRepository.intValueOnDisable();
                }
                case "boolean" -> {
                    return hardwareRepository.boolValueOnDisable();
                }
            }
        }
        if (returnType.isAssignableFrom(String.class)) {
            return hardwareRepository.stringValueOnDisable();
        }
        return null;
    }

    private Object handleCommandResult(
            HardwareQuery hardwareQuery,
            Method method,
            ErrorsHandler errorsHandler,
            String command, int retValue, List<String> inputs, List<String> errors) {
        Class<?> returnType = method.getReturnType();

        // in case we expect return num we ignore any errors
        if (returnType.isPrimitive()) {
            switch (returnType.getName()) {
                case "int" -> {
                    return retValue;
                }
                case "boolean" -> {
                    return retValue == 0;
                }
                case "void" -> {
                    return null;
                }
            }
        }

        if (retValue != 0 && !hardwareQuery.redirectErrorsToInputs()) {
            throwErrors(errorsHandler, errors);
            if (errorsHandler != null) {
                String error = errors.isEmpty() ? errorsHandler.onRetCodeError() : String.join("; ", errors);
                if (errorsHandler.logError()) {
                    logger.error(error);
                }
                if (errorsHandler.throwError()) {
                    throw new IllegalStateException(error);
                }
            } else {
                logger.error(String.format("Error while execute command '%s'. Code: '%s', Msg: '%s'%n", command, retValue,
                        String.join(", ", errors)));
                if (!hardwareQuery.ignoreOnError()) {
                    throw new HardwareException(errors, inputs, retValue);
                } else if (!hardwareQuery.valueOnError().isEmpty()) {
                    return hardwareQuery.valueOnError();
                }
            }
        } else {
            if (!hardwareQuery.redirectErrorsToInputs()) {
                for (String error : errors) {
                    if (!error.isEmpty()) {
                        logger.error(String.format("Error '%s'%n", error));
                    }
                }
            } else {
                inputs.addAll(errors);
            }
            inputs = Collections.unmodifiableCollection(inputs).stream().map(String::trim).collect(Collectors.toList());

            String joinedValue = String.join("", inputs);
            if (returnType.isAssignableFrom(String.class)) {
                return joinedValue;
            }

            if (returnType.isAssignableFrom(Integer.class)) {
                return Integer.valueOf(joinedValue);
            } else if (returnType.isAssignableFrom(Double.class)) {
                return Double.parseDouble(joinedValue);
            } else if (returnType.isAssignableFrom(Boolean.class)) {
                return Boolean.parseBoolean(joinedValue);
            }

            if (method.isAnnotationPresent(ListParse.class)) {
                ListParse listParse = method.getAnnotation(ListParse.class);
                String delimiter = listParse.delimiter();
                List<List<String>> buckets = new ArrayList<>();
                List<String> currentBucket = null;

                for (String input : inputs) {
                    if (input.matches(delimiter)) {
                        currentBucket = new ArrayList<>();
                        buckets.add(currentBucket);
                    }
                    if (currentBucket != null) {
                        currentBucket.add(input);
                    }
                }
                Class<?> genericClass = listParse.clazz();
                List<Object> result = new ArrayList<>();
                for (List<String> bucket : buckets) {
                    result.add(handleBucket(bucket, genericClass));
                }
                return result;
            } else if (method.isAnnotationPresent(LineParse.class)) {
                return handleBucket(inputs, method.getDeclaredAnnotation(LineParse.class), null);
            } else if (method.isAnnotationPresent(LineParsers.class)) {
                return handleBucket(inputs, method.getAnnotation(ListParse.LineParsers.class));
            } else if (method.isAnnotationPresent(BooleanLineParse.class)) {
                return handleBucket(inputs, method.getAnnotation(BooleanLineParse.class));
            } else if (method.isAnnotationPresent(RawParse.class)) {
                return handleBucket(inputs, method.getAnnotation(RawParse.class), null);
            } else {
                return handleBucket(inputs, returnType);
            }
        }
        return null;
    }

    @SneakyThrows
    private Object handleBucket(List<String> input, Class<?> genericClass) {
        if (Collection.class.isAssignableFrom(genericClass)) {
            if (List.class.isAssignableFrom(genericClass)) {
                return input;
            } else if (Set.class.isAssignableFrom(genericClass)) {
                return new HashSet<>(input);
            } else {
                throw new IllegalStateException("Unsupported return type: " + genericClass.getSimpleName());
            }
        }

        Object obj = newInstance(genericClass);

        boolean handleFields = false;
        SplitParse splitParse = genericClass.getDeclaredAnnotation(SplitParse.class);
        if (splitParse != null) {
            for (String item : input) {
                String[] split = item.split(splitParse.value());
                for (Field field : FieldUtils.getFieldsListWithAnnotation(genericClass, SplitParse.SplitParseIndex.class)) {
                    int splitIndex = field.getDeclaredAnnotation(SplitParse.SplitParseIndex.class).index();
                    if (splitIndex >= 0 && splitIndex < split.length) {
                        String value = split[splitIndex].trim();
                        FieldUtils.writeField(field, obj, handleType(value, field.getType()), true);
                        handleFields = true;
                    }
                }
            }
        }

        for (Field field : FieldUtils.getFieldsListWithAnnotation(genericClass, RawParse.class)) {
            Object value = handleBucket(input, field.getDeclaredAnnotation(RawParse.class), field);
            FieldUtils.writeField(field, obj, value, true);
            handleFields = true;
        }

        for (Field field : FieldUtils.getFieldsListWithAnnotation(genericClass, ListParse.LineParse.class)) {
            Object value = handleBucket(input, field.getDeclaredAnnotation(ListParse.LineParse.class), field);
            FieldUtils.writeField(field, obj, value, true);
            handleFields = true;
        }

        for (Field field : FieldUtils.getFieldsListWithAnnotation(genericClass, ListParse.BooleanLineParse.class)) {
            Object value = handleBucket(input, field.getDeclaredAnnotation(ListParse.BooleanLineParse.class));
            FieldUtils.writeField(field, obj, value, true);
            handleFields = true;
        }

        List<Field> listFields = FieldUtils.getFieldsListWithAnnotation(genericClass, ListParse.LineParsers.class);
        for (Field field : listFields) {
            ListParse.LineParsers lineParsers = field.getDeclaredAnnotation(ListParse.LineParsers.class);
            Object value = handleBucket(input, lineParsers);
            FieldUtils.writeField(field, obj, value, true);
        }

        if (!handleFields && listFields.isEmpty()) {
            if (genericClass.isAssignableFrom(String.class)) {
                return String.join("", input);
            }
        }
        if (handleFields) {
            return obj;
        }

        throw new IllegalStateException("Unsupported return type: " + genericClass.getSimpleName());
    }

    private Object handleBucket(List<String> inputs, ListParse.LineParse lineParse, Field field) {
        for (String input : inputs) {
            if (input.matches(lineParse.value())) {
                String group = findGroup(input, lineParse.value(), lineParse.group());
                if (group != null) {
                    return handleType(group.replaceAll("\"", ""), field.getType());
                }
            }
        }
        return null;
    }

    private Object handleBucket(List<String> inputs, RawParse rawParse, Field field) {
        return SystemUtils.IS_OS_WINDOWS ? newInstance(rawParse.win()).handle(inputs, field) :
                newInstance(rawParse.nix()).handle(inputs, field);
    }

    private Object handleType(String value, Class<?> type) {
        if (type.isAssignableFrom(Integer.class)) {
            return Integer.valueOf(value);
        } else if (type.isAssignableFrom(Double.class)) {
            return Double.parseDouble(value);
        }

        return value;
    }

    private Object handleBucket(List<String> inputs, ListParse.BooleanLineParse lineParse) {
        for (String input : inputs) {
            if (input.matches(lineParse.value())) {
                String group = findGroup(input, lineParse.value(), lineParse.group());
                if (group != null) {
                    if (group.equals(lineParse.when())) {
                        return !lineParse.inverse();
                    }
                }
            }
        }
        if (!lineParse.when().isEmpty()) {
            return lineParse.inverse();
        }

        return null;
    }

    private Object handleBucket(List<String> inputs, ListParse.LineParsers lineParsers) {
        for (ListParse.LineParse lineParse : lineParsers.value()) {
            Object val = handleBucket(inputs, lineParse, null);
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    private String findGroup(String input, String regexp, int group) {
        Matcher m = Pattern.compile(regexp).matcher(input);
        if (m.find()) {
            return m.group(group);
        }
        return null;
    }

    private void throwErrors(ErrorsHandler errorsHandler, List<String> errors) {
        if (!errors.isEmpty() && errorsHandler != null) {
            for (ErrorsHandler.ErrorHandler errorHandler : errorsHandler.errorHandlers()) {
                if (errors.contains(errorHandler.onError())) {
                    throw new IllegalStateException(errorHandler.throwError());
                }
            }
        }
    }

    private <T> List<Class<? extends T>> getClassesWithAnnotation() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };

        scanner.addIncludeFilter(new AnnotationTypeFilter(HardwareRepository.class));
        List<Class<? extends T>> foundClasses = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(basePackages)) {
            try {
                foundClasses.add((Class<? extends T>) Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return foundClasses;
    }

    private String getErrorMessage(Throwable ex) {
        if (ex == null) {
            return null;
        }
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        if (cause instanceof NullPointerException) {
            return "Unexpected NullPointerException at line: " + ex.getStackTrace()[0].toString();
        }

        return StringUtils.defaultString(cause.getMessage(), cause.toString());
    }

    private boolean isParamHasAnnotation(Annotation[][] apiParams, int i, Class<? extends Annotation> aClass) {
        return apiParams.length > i && apiParams[i] != null && apiParams[i].length > 0
               && aClass.isAssignableFrom(apiParams[i][0].getClass());
    }

    private HQueryExecutor buildHQueryExecutor() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return new HQueryExecutor() {
                @Override
                public String[] getValues(HardwareQuery hardwareQuery) {
                    return hardwareQuery.win();
                }

                @Override
                public void prepare(ConfigurableListableBeanFactory beanFactory, Environment env) {

                }
            };
        } else {
            return new HQueryExecutor() {
                private String pmInstall;
                private String pmUninstall;
                private String pmUpdate;

                @Override
                public String[] getValues(HardwareQuery hardwareQuery) {
                    return hardwareQuery.value();
                }

                @Override
                public String updateCommand(String cmd) {
                    if (cmd.contains("$INSTALL")) {
                        cmd = cmd.replace("$INSTALL", pmInstall);
                    }
                    if (cmd.contains("$UNINSTALL")) {
                        cmd = cmd.replace("$UNINSTALL", pmUninstall);
                    }
                    if (cmd.contains("$UPDATE")) {
                        cmd = cmd.replace("$UPDATE", pmUpdate);
                    }
                    return cmd;
                }

                @Override
                public void prepare(ConfigurableListableBeanFactory beanFactory, Environment env) {
                    pmInstall = env.getProperty("project_manager", env.getProperty("PACKAGEMANAGER", ""));
                    if (StringUtils.isEmpty(pmInstall)) {
                        MachineHardwareRepository machineHardwareRepository = beanFactory.getBean(MachineHardwareRepository.class);
                        String pm = machineHardwareRepository.getOs().getPackageManager();
                        pmInstall = pm + " install -y";
                        pmUninstall = pm + " remove -y";
                        pmUpdate = "$PM update -y && $PM full-upgrade -y && $PM autoremove -y && $PM clean -y && $PM autoclean -y";
                        if (pm.equals("apk")) {
                            pmInstall = "apk add";
                            pmUninstall = "apk del";
                            pmUpdate = "apk update";
                        }
                        logger.info("OS: " + machineHardwareRepository.getOs());
                        logger.info("PM: " + pmInstall);
                    }
                }
            };
        }
    }

    @RequiredArgsConstructor
    private static class ProcessCache {

        final int cacheValidInSec;
        final List<String> errors = new ArrayList<>();
        final List<String> inputs = new ArrayList<>();
        int retValue;
        Object response;
        long executedTime = System.currentTimeMillis();
    }
}
