/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2026 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.build.antlr;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

class AntlrLauncher {

    private final ClassLoader classLoader;
    private final List<String> extraArgs;

    static AntlrLauncher create(String extraArgs, List<Artifact> compileDeps, Log log) throws MojoExecutionException {
        URL antlr = null;

        for (Artifact artifact : compileDeps) {
            if (artifact.getFile() != null && artifact.getFile().isFile()) {
                try (JarFile jarFile = new JarFile(artifact.getFile())) {
                    ZipEntry entry = jarFile.getEntry("antlr/Tool.class");
                    if (entry != null) {
                        log.info(String.format("Using antlr Tool.class from %s:%s:%s",
                                artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
                        antlr = artifact.getFile().toURI().toURL();
                        break;
                    }
                } catch (IOException e) {
                    log.warn("Failed to probe artifact " + artifact.getId() + " for antlr.Tool", e);
                }
            }
        }
        if (antlr == null) {
            throw new MojoExecutionException("antlr.Tool class not found in compile dependencies");
        }
        return new AntlrLauncher(extraArgs, new URLClassLoader(new URL[] {antlr}, ClassLoader.getSystemClassLoader()));
    }

    private AntlrLauncher(String extraArgs, ClassLoader classLoader) {
        this.classLoader=classLoader;
        if (extraArgs == null) {
            this.extraArgs = Collections.emptyList();
        } else {
            this.extraArgs = Arrays.asList(extraArgs.split("\\s+"));
        }
    }

    private String[] args(String outdir, String inputFile) {
        List<String> args = new ArrayList<>(extraArgs);
        args.add("-o");
        args.add(outdir);
        args.add(inputFile);
        return args.toArray(new String[0]);
    }

    void execute(String outdir, String inputFile) throws MojoExecutionException {
        PrintStream oldErr = System.err;
        ByteArrayOutputStream errOS = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errOS);
        System.setErr(err);
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        // Believe or not, this plugin also has transitive dependency on ANTLR 2, so we need to switch context class loading as well.
        Thread.currentThread().setContextClassLoader(classLoader);

        try {
            classLoader.loadClass("antlr.Tool")
                    .getMethod("main", new Class[] { String[].class })
                    .invoke(null, new Object[] { args(outdir, inputFile) });
        } catch (ClassNotFoundException e) {
            throw new MojoExecutionException("could not locate class antlr.Tool", e);
        } catch (NoSuchMethodException e) {
            throw new MojoExecutionException("error locating antlt.Tool#main", e);
        } catch (InvocationTargetException e) {
            throw new MojoExecutionException("error executing antlt.Tool#main", e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new MojoExecutionException("error executing antlt.Tool#main", e);
        } catch (RuntimeException e) {
            try {
                throw new MojoExecutionException("Antlr execution failed: "
                        + e.getMessage() + "\n Error output:\n"
                        + errOS.toString("UTF-8"), e);
            } catch (UnsupportedEncodingException ex) {
                // UTF-8 cannot be unsupported
                throw new AssertionError(ex);
            }
        } finally {
            System.setErr(oldErr);
            Thread.currentThread().setContextClassLoader(context);
        }
    }
}
