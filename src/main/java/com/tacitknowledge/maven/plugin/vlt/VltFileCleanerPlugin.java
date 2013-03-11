package com.tacitknowledge.maven.plugin.vlt;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Goal which remove all .vlt files.
 * 
 * @goal clean
 * @phase clean
 */
public class VltFileCleanerPlugin extends AbstractMojo {

    /**
     * The name of the generated JAR file.
     * 
     * @parameter expression="${my.sourceDirectory}" default-value=
     *            "${basedir}"
     * @required
     */
    private String sourceDirectory;

    /**
     * inherited.
     * 
     * {@inheritDoc}
     * 
     * @see org.apache.maven.plugin.AbstractMojo#execute() {@inheritDoc}
     */
    public final void execute() throws MojoExecutionException {
        File startDirectory = new File(sourceDirectory);
        deleteVltFiles(startDirectory);
    }

    /**
     * @param startDirectory
     */
    private void deleteVltFiles(File startDirectory) {
        File[] vltFiles = startDirectory.listFiles(new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return name.endsWith(".vlt");
            }

        });
        for (int i = 0; i < vltFiles.length; i++) {
            getLog().info("removing " + vltFiles[i].getAbsolutePath());
            vltFiles[i].delete();
        }
        File[] allDir = startDirectory.listFiles(new FileFilter() {

            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }

        });
        for (int i = 0; i < allDir.length; i++) {
            deleteVltFiles(allDir[i]);
        }
    }
}
