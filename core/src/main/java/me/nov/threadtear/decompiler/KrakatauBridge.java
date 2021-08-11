package me.nov.threadtear.decompiler;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class KrakatauBridge implements IDecompilerBridge {
  private static boolean setup;
  private static Path krakatau;


  @Override
  public String decompile(File archive, String name, byte[] bytes) {
    if (!setup) {
      String error = setupKrakatau();
      if (error != null) {
        return "Failed to unzip krakatau in temp directory.\n" + error;
      }
    }
    File krakatauIn;
    try {
      krakatauIn = writeJar(name, bytes);
    } catch (Throwable t) {
      t.printStackTrace();
      StringWriter sw = new StringWriter();
      sw.append("Failed to make temp jar for class \"").append(name).append("\"\n");
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);
      return sw.toString();
    }
    try {
      final String rtPath = getRuntimeJarPath();
      StringBuilder output = new StringBuilder();
      File krakatauOut = Files.createTempFile(name.hashCode() + "-decompiled", ".jar").toFile();
      List<String> args = new ArrayList<>(Arrays.asList("python", "decompile.py", "-skip", "-out", krakatauOut.getAbsolutePath(), krakatauIn.getAbsolutePath(), "-path", archive.getAbsolutePath()));
      if(rtPath != null) args.addAll(Arrays.asList("-path", rtPath, "-nauto"));
      else output.append("/* Cannot find rt.jar, this may cause decompilation errors */\n");

      ProcessBuilder pb = new ProcessBuilder(args);
      pb.directory(krakatau.toFile());

      pb.redirectErrorStream(true);
      Process p = pb.start();
      p.waitFor();
      output.append(readOutput(krakatauOut));
      return output.toString();
    } catch (Throwable t) {
      if (t.getMessage() != null && t.getMessage().contains("Cannot run program")) {
        return "Could not run python executable. Please set your python 2.7 path correctly to use krakatau" +
          ".\nError: " + t.getMessage() + "\n\n/*\nYour environment variables:\n" +
          System.getenv().entrySet().stream().map(e -> e.getKey() + " = \"" + e.getValue() + "\"")
            .collect(Collectors.joining("\n")) + "\n*/";
      }
      t.printStackTrace();
      StringWriter sw = new StringWriter();
      sw.append("Failed krakatau execution for class \"").append(name).append("\"\n");
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);
      return sw.toString();
    }
  }

  private String readOutput(File krakatauOut) throws IOException {
    try (JarFile jar = new JarFile(krakatauOut)) {
      if (!jar.entries().hasMoreElements()) {
        return "Error: Krakatau output file is empty.";
      }
      return IOUtils.toString(jar.getInputStream(jar.entries().nextElement()), StandardCharsets.UTF_8);
    }
  }

  private File writeJar(String name, byte[] bytes) throws IOException {
    File output = Files.createTempFile(name.hashCode() + "-", ".jar").toFile();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(output))) {
      out.putNextEntry(new JarEntry("Target.class"));
      out.write(bytes);
      out.closeEntry();
    }
    return output;
  }

  @Override
  public void setAggressive(boolean aggressive) {
  }

  /**
   * @return error, if failed
   */
  private static String setupKrakatau() {
    try {
      krakatau = Files.createTempDirectory("krakatau");
      ZipInputStream zin = new ZipInputStream(
        Objects.requireNonNull(KrakatauBridge.class.getResourceAsStream("krakatau.zip")));
      ZipEntry zipEntry;
      while ((zipEntry = zin.getNextEntry()) != null) {
        Path resolvedPath = krakatau.resolve(zipEntry.getName()).normalize();
        if (zipEntry.isDirectory()) {
          Files.createDirectories(resolvedPath);
        } else {
          if (!Files.isDirectory(resolvedPath.getParent())) {
            Files.createDirectories(resolvedPath.getParent());
          }
          try (FileOutputStream outStream = new FileOutputStream(resolvedPath.toFile())) {
            IOUtils.copy(zin, outStream);
          }
        }
      }
      zin.close();
      setup = true;
      return null;
    } catch (Throwable t) {
      t.printStackTrace();
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);
      return sw.toString();
    }
  }

  private static String getRuntimeJarPath() {
    String rtPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    return new File(rtPath).exists() ? rtPath : null;
  }

  public static class KrakatauDecompilerInfo extends DecompilerInfo<KrakatauBridge> {

    @Override
    public String getName() {
      return "Krakatau";
    }

    @Override
    public String getVersionInfo() {
      return "22-05-20";
    }

    @Override
    public KrakatauBridge createDecompilerBridge() {
      return new KrakatauBridge();
    }
  }
}
