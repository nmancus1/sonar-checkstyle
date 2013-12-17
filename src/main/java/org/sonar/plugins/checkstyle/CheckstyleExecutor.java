/*
 * SonarQube Checkstyle Plugin
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.checkstyle;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.PackageNamesLoader;
import com.puppycrawl.tools.checkstyle.XMLLogger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchExtension;
import org.sonar.api.batch.ProjectClasspath;
import org.sonar.api.utils.SonarException;
import org.sonar.api.utils.TimeProfiler;

import java.io.File;
import java.io.OutputStream;
import java.util.Locale;

public class CheckstyleExecutor implements BatchExtension {
  private static final Logger LOG = LoggerFactory.getLogger(CheckstyleExecutor.class);

  private final CheckstyleConfiguration configuration;
  private final ClassLoader projectClassloader;
  private final CheckstyleAuditListener listener;

  public CheckstyleExecutor(CheckstyleConfiguration configuration, CheckstyleAuditListener listener, ProjectClasspath classpath) {
    this.configuration = configuration;
    this.listener = listener;
    this.projectClassloader = classpath.getClassloader();
  }

  CheckstyleExecutor(CheckstyleConfiguration configuration, CheckstyleAuditListener listener, ClassLoader projectClassloader) {
    this.configuration = configuration;
    this.listener = listener;
    this.projectClassloader = projectClassloader;
  }

  /**
   * Execute Checkstyle and return the generated XML report.
   */
  public void execute() {
    TimeProfiler profiler = new TimeProfiler().start("Execute Checkstyle " + CheckstyleVersion.getVersion());
    ClassLoader initialClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(PackageNamesLoader.class.getClassLoader());

    Locale initialLocale = Locale.getDefault();
    Locale.setDefault(Locale.ENGLISH);
    Checker checker = null;
    OutputStream xmlOutput = null;
    try {

      checker = new Checker();
      checker.setClassloader(projectClassloader);
      checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
      checker.addListener(listener);

      File xmlReport = configuration.getTargetXMLReport();
      if (xmlReport != null) {
        LOG.info("Checkstyle output report: " + xmlReport.getAbsolutePath());
        xmlOutput = FileUtils.openOutputStream(xmlReport);
        checker.addListener(new XMLLogger(xmlOutput, true));
      }

      checker.setCharset(configuration.getCharset().name());
      checker.configure(configuration.getCheckstyleConfiguration());
      checker.process(configuration.getSourceFiles());

      profiler.stop();

    } catch (Exception e) {
      throw new SonarException("Can not execute Checkstyle", e);

    } finally {
      if (checker != null) {
        checker.destroy();
      }
      IOUtils.closeQuietly(xmlOutput);
      Thread.currentThread().setContextClassLoader(initialClassLoader);
      Locale.setDefault(initialLocale);
    }
  }

}
