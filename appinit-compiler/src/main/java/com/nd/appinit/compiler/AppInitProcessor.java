package com.nd.appinit.compiler;

import com.nd.appinit.annotation.AppInit;
import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@AutoService(Processor.class)
public class AppInitProcessor extends AbstractProcessor {

    private static final String BASE_PACKAGE = "com.nd.appinit.processor";
    private static final String OPTION_MODULE_NAME = "appinit.module.name";

    private String moduleName;
    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;
    private final Map<String, TypeElement> initClasses = new LinkedHashMap<>();
    private boolean generated;
    private boolean hasError;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        String configuredModuleName = processingEnv.getOptions().get(OPTION_MODULE_NAME);
        if (configuredModuleName == null || configuredModuleName.trim().isEmpty()) {
            printError("AppInit: module name not configured. Please add the following in your build.gradle:\n\n" +
                    "kapt {\n" +
                    "    arguments {\n" +
                    "        arg(\"appinit.module.name\", \"your_module_name\")\n" +
                    "    }\n" +
                    "}");
            throw new RuntimeException("AppInit: module name is required. Please configure 'appinit.module.name' in kapt arguments.");
        }
        moduleName = sanitizeModuleName(configuredModuleName.trim());
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(AppInit.class.getCanonicalName());
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.singleton(OPTION_MODULE_NAME);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (generated) {
            return true;
        }

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(AppInit.class);

        for (Element element : elements) {
            if (element.getKind() != ElementKind.CLASS) {
                printError("@AppInit only applies to classes", element);
                continue;
            }

            TypeElement typeElement = (TypeElement) element;

            if (!isAppInitListener(typeElement)) {
                printError(
                        "@AppInit class must implement com.nd.appinit.IAppInitListener: " + typeElement.getQualifiedName().toString(),
                        typeElement);
                continue;
            }

            // 检查是否有无参构造函数
            if (hasNoArgConstructor(typeElement)) {
                initClasses.put(typeElement.getQualifiedName().toString(), typeElement);
            } else {
                printError(
                        "@AppInit class must have a public no-arg constructor: " + typeElement.getQualifiedName().toString(),
                        typeElement);
            }
        }

        generateCollectedAppInitClass();

        return true;
    }

    private void generateCollectedAppInitClass() {
        if (generated || hasError || initClasses.isEmpty()) {
            return;
        }

        try {
            generateAppInitClass(new java.util.ArrayList<>(initClasses.values()));
            generated = true;
        } catch (IOException e) {
            printError("Failed to generate AppInit class: " + e.getMessage());
        }
    }

    private boolean hasNoArgConstructor(TypeElement typeElement) {
        boolean hasConstructor = false;
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                hasConstructor = true;
                if (enclosed.getModifiers().contains(Modifier.PUBLIC) &&
                        ((javax.lang.model.element.ExecutableElement) enclosed).getParameters().isEmpty()) {
                    return true;
                }
            }
        }
        return !hasConstructor;
    }

    private boolean isAppInitListener(TypeElement typeElement) {
        TypeElement listenerElement = elementUtils.getTypeElement("com.nd.appinit.IAppInitListener");
        if (listenerElement == null) {
            printError("AppInit: com.nd.appinit.IAppInitListener not found. Please add appinit-runtime dependency.");
            return false;
        }
        TypeMirror listenerType = typeUtils.erasure(listenerElement.asType());
        TypeMirror targetType = typeUtils.erasure(typeElement.asType());
        return typeUtils.isAssignable(targetType, listenerType);
    }

    private String sanitizeModuleName(String rawModuleName) {
        StringBuilder builder = new StringBuilder(rawModuleName.length());
        for (int i = 0; i < rawModuleName.length(); i++) {
            char ch = rawModuleName.charAt(i);
            builder.append(Character.isJavaIdentifierPart(ch) ? ch : '_');
        }
        return builder.length() == 0 ? "module" : builder.toString();
    }

    private void printError(String message) {
        hasError = true;
        messager.printMessage(Diagnostic.Kind.ERROR, message);
    }

    private void printError(String message, Element element) {
        hasError = true;
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void generateAppInitClass(List<TypeElement> initClasses) throws IOException {
        // 返回类型: List<com.nd.appinit.AppInitInfo>
        com.squareup.javapoet.ClassName appInitInfoClass = com.squareup.javapoet.ClassName.get("com.nd.appinit", "AppInitInfo");
        com.squareup.javapoet.ClassName appInitProcessClass = com.squareup.javapoet.ClassName.get("com.nd.appinit.annotation", "AppInitProcess");
        com.squareup.javapoet.ParameterizedTypeName listOfAppInitInfo = com.squareup.javapoet.ParameterizedTypeName.get(
                com.squareup.javapoet.ClassName.get(List.class),
                appInitInfoClass);

        // 生成方法
        com.squareup.javapoet.MethodSpec.Builder methodBuilder = com.squareup.javapoet.MethodSpec.methodBuilder("getAllAppInitClass")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC)
                .returns(listOfAppInitInfo);

        // 添加初始化语句
        methodBuilder.addStatement("$T<$T> result = new $T<>()", List.class, appInitInfoClass, java.util.ArrayList.class);

        // 添加每个 listener
        for (TypeElement cls : initClasses) {
            AppInit ann = cls.getAnnotation(AppInit.class);
            int priority = ann != null ? ann.priority() : 0;
            String process = ann != null ? ann.process().name() : "ALL";
            methodBuilder.addStatement("result.add(new $T(new $T(), $L, $T.$L))", appInitInfoClass,
                    com.squareup.javapoet.ClassName.get(cls), priority, appInitProcessClass, process);
        }

        methodBuilder.addStatement("return result");

        com.squareup.javapoet.MethodSpec getAllInitializers = methodBuilder.build();

        // 类名: AppInitWareHouse${模块名}
        String className = "AppInitWareHouse$" + moduleName;

        // 生成 AppInitWareHouse 类
        com.squareup.javapoet.TypeSpec appInitClass = com.squareup.javapoet.TypeSpec.classBuilder(className)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.FINAL)
                .addMethod(getAllInitializers)
                .build();

        com.squareup.javapoet.JavaFile.builder(BASE_PACKAGE, appInitClass)
                .build()
                .writeTo(filer);
    }
}
