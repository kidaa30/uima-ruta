/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.uima.ruta.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.antlr.runtime.RecognitionException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.ruta.descriptor.RutaBuildOptions;
import org.apache.uima.ruta.descriptor.RutaDescriptorFactory;
import org.apache.uima.ruta.descriptor.RutaDescriptorInformation;
import org.apache.uima.ruta.extensions.IRutaExtension;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLizable;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.xml.sax.SAXException;

/**
 * Generate descriptors from UIMA Ruta script files.
 * 
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class RutaGenerateDescriptorMojo extends AbstractMojo {
  private static final String DEFAULT_TARGET_DIR = "${project.build.directory}/generated-sources/ruta/descriptor";

  private static final String RUTA_NATURE = "org.apache.uima.ruta.ide.nature";

  @Component
  private MavenProject project;

  @Component
  private BuildContext buildContext;

  /**
   * The directory where the generated type system descriptors will be written.
   */
  @Parameter(defaultValue = DEFAULT_TARGET_DIR, required = true)
  private File typeSystemOutputDirectory;

  /**
   * The directory where the generated analysis engine descriptors will be written.
   */
  @Parameter(defaultValue = DEFAULT_TARGET_DIR, required = true)
  private File analysisEngineOutputDirectory;

  /**
   * The template descriptor for the generated type system.
   */
  @Parameter(required = false)
  private File typeSystemTemplate;

  /**
   * The template descriptor for the generated analysis engine.
   */
  @Parameter(required = false)
  private File analysisEngineTemplate;

  /**
   * Script paths of the generated analysis engine descriptor.
   */
  @Parameter(required = false)
  private String[] scriptPaths;

  /**
   * Descriptor paths of the generated analysis engine descriptor.
   */
  @Parameter(defaultValue = DEFAULT_TARGET_DIR, required = false)
  private String[] descriptorPaths;

  /**
   * Resource paths of the generated analysis engine descriptor.
   */
  @Parameter(defaultValue = DEFAULT_TARGET_DIR, required = false)
  private String[] resourcePaths;

  /**
   * Suffix used for the generated type system descriptors.
   */
  @Parameter(defaultValue = "TypeSystem", required = true)
  private String typeSystemSuffix;

  /**
   * Suffix used for the generated analysis engine descriptors.
   */
  @Parameter(defaultValue = "Engine", required = true)
  private String analysisEngineSuffix;

  /**
   * Source file encoding.
   */
  @Parameter(defaultValue = "${project.build.sourceEncoding}", required = true)
  private String encoding;

  /**
   * Type of type system imports. default false = import by location.
   */
  @Parameter(defaultValue = "false", required = false)
  private boolean importByName;

  /**
   * Option to resolve imports while building.
   */
  @Parameter(defaultValue = "false", required = false)
  private boolean resolveImports;

  /**
   * Amount of retries for building dependent descriptors.
   */
  @Parameter(defaultValue = "-1", required = false)
  private int maxBuildRetries;

  /**
   * List of packages with language extensions
   */
  @Parameter(defaultValue = "org.apache.uima.ruta", required = false)
  private String[] extensionPackages;

  /**
   * Add UIMA Ruta nature to .project
   */
  @Parameter(defaultValue = "true", required = false)
  private boolean addRutaNature;

  public void execute() throws MojoExecutionException, MojoFailureException {

    if (!typeSystemOutputDirectory.exists()) {
      typeSystemOutputDirectory.mkdirs();
      buildContext.refresh(typeSystemOutputDirectory);
    }

    if (!analysisEngineOutputDirectory.exists()) {
      analysisEngineOutputDirectory.mkdirs();
      buildContext.refresh(analysisEngineOutputDirectory);
    }

    RutaDescriptorFactory factory = new RutaDescriptorFactory();
    if (typeSystemTemplate != null) {
      try {
        factory.setDefaultTypeSystem(typeSystemTemplate.toURI().toURL());
      } catch (MalformedURLException e) {
        getLog().warn("Failed to get URL of " + typeSystemTemplate, e);
      }
    }
    if (analysisEngineTemplate != null) {
      try {
        factory.setDefaultEngine(analysisEngineTemplate.toURI().toURL());
      } catch (MalformedURLException e) {
        getLog().warn("Failed to get URL of " + analysisEngineTemplate, e);
      }
    }

    URLClassLoader classloader = getClassloader(project, getLog());

    RutaBuildOptions options = new RutaBuildOptions();
    options.setTypeSystemSuffix(typeSystemSuffix);
    options.setAnalysisEngineSuffix(analysisEngineSuffix);
    options.setEncoding(encoding);
    options.setResolveImports(resolveImports);
    options.setImportByName(importByName);

    List<String> extensions = getExtensionsFromClasspath(classloader);
    options.setLanguageExtensions(extensions);

    String[] files = FileUtils.getFilesFromExtension(project.getBuild().getOutputDirectory(),
            new String[] { "ruta" });

    if (maxBuildRetries == -1) {
      maxBuildRetries = files.length * 3;
    }

    Queue<RutaDescriptorInformation> toBuild = new LinkedList<RutaDescriptorInformation>();

    for (String fileString : files) {
      File file = new File(fileString);
      try {
        RutaDescriptorInformation descriptorInformation = factory.parseDescriptorInformation(file,
                encoding);
        toBuild.add(descriptorInformation);
      } catch (RecognitionException re) {
        getLog().warn("Failed to parse UIMA Ruta script file: " + file.getAbsolutePath(), re);
      } catch (IOException ioe) {
        getLog().warn("Failed to load UIMA Ruta script file: " + file.getAbsolutePath(), ioe);
      }
    }

    int count = 0;
    while (!toBuild.isEmpty() && count <= maxBuildRetries) {
      RutaDescriptorInformation descriptorInformation = toBuild.poll();
      String scriptName = descriptorInformation.getScriptName();
      try {
        createDescriptors(factory, classloader, options, descriptorInformation);
      } catch (RecognitionException re) {
        getLog().warn("Failed to parse UIMA Ruta script: " + scriptName, re);
      } catch (IOException ioe) {
        toBuild.add(descriptorInformation);
        count++;
      } catch (SAXException saxe) {
        getLog().warn("Failed to write descriptor: " + scriptName, saxe);
      } catch (URISyntaxException urise) {
        getLog().warn("Failed to get uri: " + scriptName, urise);
      } catch (ResourceInitializationException rie) {
        getLog().warn("Failed initialize resource: " + scriptName, rie);
      } catch (InvalidXMLException ixmle) {
        getLog().warn("Invalid XML while building descriptor: " + scriptName, ixmle);
      }
    }

    for (RutaDescriptorInformation eachFailed : toBuild) {
      String scriptName = eachFailed.getScriptName();
      getLog().warn("Failed to build UIMA Ruta script: " + scriptName);
    }

    if (addRutaNature) {
      addRutaNature();
    }

  }

  private List<String> getExtensionsFromClasspath(ClassLoader classloader) {
    List<String> result = new ArrayList<String>();
    ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(
            true);
    ResourceLoader resourceLoader = new DefaultResourceLoader(classloader);
    provider.setResourceLoader(resourceLoader);
    provider.addIncludeFilter(new AssignableTypeFilter(IRutaExtension.class));

    for (String basePackage : extensionPackages) {
      Set<BeanDefinition> components = provider.findCandidateComponents(basePackage);
      for (BeanDefinition component : components) {
        String beanClassName = component.getBeanClassName();
        result.add(beanClassName);
      }
    }
    return result;

  }

  private void createDescriptors(RutaDescriptorFactory factory, URLClassLoader classloader,
          RutaBuildOptions options, RutaDescriptorInformation descriptorInformation)
          throws IOException, RecognitionException, InvalidXMLException,
          ResourceInitializationException, URISyntaxException, SAXException {
    String packageString = descriptorInformation.getPackageString().replaceAll("[.]", "/");
    String engineOutput = new File(analysisEngineOutputDirectory, packageString + "/"
            + descriptorInformation.getScriptName() + analysisEngineSuffix + ".xml")
            .getAbsolutePath();
    String typeSystemOutput = new File(typeSystemOutputDirectory, packageString + "/"
            + descriptorInformation.getScriptName() + typeSystemSuffix + ".xml").getAbsolutePath();
    Pair<AnalysisEngineDescription, TypeSystemDescription> descriptions = factory
            .createDescriptions(engineOutput, typeSystemOutput, descriptorInformation, options,
                    scriptPaths, descriptorPaths, resourcePaths, classloader);
    write(descriptions.getKey(), engineOutput);
    write(descriptions.getValue(), typeSystemOutput);
    buildContext.refresh(analysisEngineOutputDirectory);
    buildContext.refresh(typeSystemOutputDirectory);
  }

  private void write(XMLizable desc, String aFilename) throws SAXException, IOException {
    OutputStream os = null;
    try {
      File out = new File(aFilename);
      out.getParentFile().mkdirs();
      getLog().debug("Writing descriptor to: " + out);
      os = new FileOutputStream(out);
      desc.toXML(os);
    } finally {
      IOUtils.closeQuietly(os);
    }
  }

  /**
   * Create a class loader which covers the classes compiled in the current project and all
   * dependencies.
   */
  public static URLClassLoader getClassloader(MavenProject aProject, Log aLog)
          throws MojoExecutionException {
    List<URL> urls = new ArrayList<URL>();
    try {
      for (Object object : aProject.getCompileClasspathElements()) {
        String path = (String) object;
        aLog.debug("Classpath entry: " + object);
        urls.add(new File(path).toURI().toURL());
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Unable to assemble classpath: "
              + ExceptionUtils.getRootCauseMessage(e), e);
    } catch (DependencyResolutionRequiredException e) {
      throw new MojoExecutionException("Unable to resolve dependencies: "
              + ExceptionUtils.getRootCauseMessage(e), e);
    }

    for (Artifact dep : (Set<Artifact>) aProject.getDependencyArtifacts()) {
      try {
        if (dep.getFile() == null) {
          // Unresolved file because it is in the wrong scope (e.g. test?)
          continue;
        }
        aLog.debug("Classpath entry: " + dep.getGroupId() + ":" + dep.getArtifactId() + ":"
                + dep.getVersion() + " -> " + dep.getFile());
        urls.add(dep.getFile().toURI().toURL());
      } catch (Exception e) {
        throw new MojoExecutionException("Unable get dependency artifact location for "
                + dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion()
                + ExceptionUtils.getRootCauseMessage(e), e);
      }
    }
    return new URLClassLoader(urls.toArray(new URL[] {}),
            RutaGenerateDescriptorMojo.class.getClassLoader());
  }

  private void addRutaNature() {

    File projectDir = project.getFile().getParentFile();
    File projectFile = new File(projectDir, ".project");
    if (projectFile.exists()) {
      Xpp3Dom project = null;
      try {
        project = Xpp3DomBuilder.build(new FileReader(projectFile));
      } catch (XmlPullParserException | IOException e) {
        getLog().warn("Failed to access .project file", e);
      }
      if (project == null) {
        return;
      }

      Xpp3Dom naturesNode = project.getChild("natures");
      if (naturesNode != null) {
        for (int i = 0; i < naturesNode.getChildCount(); ++i) {
          Xpp3Dom natureEntry = naturesNode.getChild(i);
          if (natureEntry != null && StringUtils.equals(natureEntry.getValue(), RUTA_NATURE)) {
            return;
          }
        }
      }
      Xpp3Dom rutaNatureNode = new Xpp3Dom("nature");
      rutaNatureNode.setValue(RUTA_NATURE);
      naturesNode.addChild(rutaNatureNode);

      StringWriter sw = new StringWriter();
      Xpp3DomWriter.write(sw, project);
      String string = sw.toString();
      // Xpp3DomWriter creates empty string with file writer, check before writing to file
      if (!StringUtils.isBlank(string)) {
        try {
          FileUtils.fileWrite(projectFile, encoding, string);
        } catch (IOException e) {
          getLog().warn("Failed to write .project file", e);
        }
      }
      buildContext.refresh(projectDir);
    }
  }
}