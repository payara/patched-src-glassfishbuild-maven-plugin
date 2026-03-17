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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;

import static org.codehaus.plexus.util.StringUtils.isEmpty;

/**
 * Execute ANTLR 2 as required by some of Payara modules.
 * Originally we would invoke Antlr plugin, which required classpath workarounds, and contained lots of options we didn't use.
 * The plugin will output the sources under outputdir disregarding the packages, which is ok, though we rarely remember that.
 *
 *
 * @author Patrik Dudits
 * @version $Id$
 *
 * @goal antlr
 * @phase generate-sources
 * @requiresDependencyResolution compile
 */
public class AntlrMojo extends AbstractMojo {

    /**
     * Specifies the Antlr directory containing grammar files.
     *
     * @parameter default-value="${basedir}/src/main/antlr"
     */
    protected File sourceDirectory;

    /**
     * The Maven Project Object
     *
     * @parameter expression="${project}"
     * @readonly
     */
    protected MavenProject project;

    /**
     * Specifies the destination directory where Antlr should generate files. <br/>
     * See <a href="http://www.antlr2.org/doc/options.html#Command%20Line%20Options">Command Line Options</a>
     *
     * @parameter default-value="${project.build.directory}/generated-sources/antlr"
     */
    protected File outputDirectory;

    /**
     * Comma separated grammar file names or grammar pattern file names present in the <code>sourceDirectory</code>
     * directory. <br/>
     * See <a href="http://www.antlr2.org/doc/options.html#Command%20Line%20Options">Command Line Options</a>
     *
     * @parameter expression="${antlr.grammars}"
     */
    protected String grammars;

    /**
     * Extra arguments to antlr, such as <code>-trace</code> or <code>-diagnostic</code>, as per
     * <a href="http://www.antlr2.org/doc/options.html#Command%20Line%20Options">Command Line Options</a>.
     * Split into separate arguments by whitespace.
     *
     * @parameter expression="${antlr.args}"
     */
    protected String antlrArgs;

    /**
     * @throws MojoExecutionException
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (isEmpty(grammars)) {
            throw new MojoExecutionException("Grammars list is not defined via <grammars>");
        }

        // ANTLR liked to do System.exit(1) as error handling mechanism
        System.setProperty("ANTLR_DO_NOT_EXIT", "true");

        @SuppressWarnings("unchecked")
        AntlrLauncher launcher = AntlrLauncher.create(antlrArgs, project.getCompileArtifacts(), getLog());

        outputDirectory.mkdirs();
        String outputDir = outputDirectory.getAbsolutePath();

        for(String grammar : grammars.split(",\\s*")) {
            getLog().info("performing grammar generation [" + grammar + "]");
            launcher.execute(outputDir, new File(sourceDirectory, grammar).getAbsolutePath());
        }
        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
    }

}
