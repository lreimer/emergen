/*
 * MIT License
 *
 * Copyright (c) 2018 QAware GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package de.qaware.emergen.apt.lang;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * An annotation processor implementation to generate the Java ServiceLoader files
 * for any types annotated with the ServiceLoaderSupport annotation.
 *
 * @author lreimer
 */
@SupportedAnnotationTypes({"de.qaware.emergen.apt.lang.ServiceLoaderSupport"})
public class ServiceLoaderSupportProcessor extends AbstractProcessor {

    private static final String EMPTY_PACKAGE = "";
    private static final String BASEPATH = "META-INF/services/";
    private static final String LINE_SEPARATOR = "line.separator";

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }

        Map<String, List<String>> services = new HashMap<>();
        for (TypeElement typeElement : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(typeElement)) {
                ServiceLoaderSupport serviceAnnotation = element.getAnnotation(ServiceLoaderSupport.class);

                String serviceInterface = serviceAnnotation.value();
                if ("".equals(serviceInterface)) {
                    List<? extends TypeMirror> interfaces = ((TypeElement) element).getInterfaces();
                    if (!interfaces.isEmpty()) {
                        TypeMirror interfaceMirror = interfaces.get(0);
                        Element interfaceElement = ((DeclaredType) interfaceMirror).asElement();
                        serviceInterface = ((TypeElement) interfaceElement).getQualifiedName().toString();
                    }
                }

                if (!services.containsKey(serviceInterface)) {
                    services.put(serviceInterface, new ArrayList<>());
                }

                // add the annotated type element full qualified name
                services.get(serviceInterface).add(((TypeElement) element).getQualifiedName().toString());
            }
        }

        // now process all the services with their implementation
        Messager messager = processingEnv.getMessager();
        Filer filer = processingEnv.getFiler();
        for (Map.Entry<String, List<String>> service : services.entrySet()) {
            String filename = BASEPATH.concat(service.getKey());
            messager.printMessage(Diagnostic.Kind.OTHER, "Writing service file " + filename);

            try (Writer writer = filer.createResource(StandardLocation.SOURCE_OUTPUT, EMPTY_PACKAGE, filename).openWriter()) {
                for (String implementation : service.getValue()) {
                    writer.write(implementation);
                    writer.write(System.getProperty(LINE_SEPARATOR));
                }
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
                throw new IllegalArgumentException(e);
            }
        }

        return true;
    }

    /**
     * We support the latest source version of the current execution environment. This is the alternative
     * to using the {@link javax.annotation.processing.SupportedSourceVersion} annotation.
     *
     * @return the latest source version
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
