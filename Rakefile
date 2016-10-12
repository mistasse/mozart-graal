require_relative 'tool/common'

OZWISH = MOZART2 / "wish/ozwish"
OZWISH_SRC = MOZART2 / "wish/unixmain.cc"

BOOTCOMPILER_ECLIPSE = BOOTCOMPILER / ".project"

TRUFFLE_API_SRC = TRUFFLE / "mxbuild/dists/truffle-api.src.zip"
TRUFFLE_DSL_PROCESSOR_JAR = TRUFFLE / "mxbuild/dists/truffle-dsl-processor.jar"

JVMCI_HOME = JVMCI / "jdk1.8.0_92/product"
GRAAL_MX_ENV = GRAAL / "mx.graal-core/env"

JAVA_SOURCES = Dir["src/**/*.java"]

def erb(template, output)
  require 'erb'
  File.write output, ERB.new(File.read(template), nil, '<>').result(binding)
end

namespace :build do
  task :all => [:truffle, :mozart2, :bootcompiler, :project]

  task :mozart2 => [MOZART2, :ozwish]
  task :bootcompiler => [MOZART2, BOOTCOMPILER_JAR, BOOTCOMPILER_ECLIPSE]
  task :ozwish => OZWISH

  task :truffle => TRUFFLE_API_JAR

  desc "Build Graal"
  task :graal => GRAAL_JAR

  task :project => [:javac, ".classpath", ".factorypath"]
  task :javac => MAIN_CLASS

  file MOZART2 do
    sh "cd .. && git clone https://github.com/eregon/mozart2.git"
    sh "cd #{MOZART2} && git checkout mozart-graal"
  end

  file OZWISH => OZWISH_SRC do
    begin
      sh "cc -o #{OZWISH} -ltcl -ltk #{OZWISH_SRC}"
    rescue
      puts "WARNING: Failed to build ozwish"
    end
  end

  file BOOTCOMPILER_JAR => Dir[BOOTCOMPILER / "src/**/*.scala"] + [TRUFFLE_API_JAR] do
    sh "cd #{BOOTCOMPILER} && ./sbt assembly"
    touch "#{BOOTCOMPILER_JAR}" # sbt might not update mtime
  end

  file BOOTCOMPILER_ECLIPSE do
    sh "cd #{BOOTCOMPILER} && ./sbt eclipse eclipse-with-source"
  end

  file MX do
    sh "cd .. && git clone https://github.com/graalvm/mx.git"
  end

  file TRUFFLE do
    sh "cd .. && git clone https://github.com/eregon/truffle.git"
    sh "cd #{TRUFFLE} && git checkout coro"
  end

  file TRUFFLE_API_JAR => [MX, TRUFFLE] do
    sh "cd #{TRUFFLE} && #{MX} build"
  end
  file TRUFFLE_DSL_PROCESSOR_JAR => TRUFFLE_API_JAR

  file JVMCI do
    sh "cd .. && git clone https://github.com/eregon/jvmci.git"
    sh "cd #{JVMCI} && git checkout coro"
  end

  file GRAAL => TRUFFLE do
    sh "git clone https://github.com/eregon/graal-core.git #{GRAAL}"
    sh "cd #{GRAAL} && git checkout coro"
  end

  file JVMCI_HOME => [JVMCI, MX] do
    sh "echo 'Choose JDK 1.8.0_92 when asked for JAVA_HOME' && echo"
    sh "cd #{JVMCI} && #{MX} build"
    sh "cd #{JVMCI_HOME} && bin/java -version"
    JVMCI_HOME.touch
  end

  file GRAAL_MX_ENV => JVMCI_HOME do
    GRAAL_MX_ENV.write("JAVA_HOME=#{JVMCI_HOME}\n") unless GRAAL_MX_ENV.exist?
  end

  file GRAAL_JAR => [GRAAL, MX, GRAAL_MX_ENV] do
    sh "cd #{GRAAL} && #{MX} build"
  end

  file ".classpath" => "tool/classpath.erb" do
    sh "mvn dependency:build-classpath"
    erb 'tool/classpath.erb', '.classpath'
  end

  file ".factorypath" => "tool/factorypath.erb" do
    erb 'tool/factorypath.erb', '.factorypath'
  end

  directory "bin"

  file MAIN_CLASS => ["bin", TRUFFLE_API_JAR, TRUFFLE_DSL_PROCESSOR_JAR, BOOTCOMPILER_JAR, ".classpath", *JAVA_SOURCES] do
    sh *["javac", "-cp", "#{TRUFFLE_API_JAR}:#{TRUFFLE_DSL_PROCESSOR_JAR}:#{oz_classpath.join(':')}", "-sourcepath", "src", "-d", "bin", *JAVA_SOURCES]
  end
end

desc "Build the project and dependencies"
task :build => "build:all"

desc "Run tests"
task :test do
  exec './oz'
end

task :default => [:build, :test]

desc "Update all repositories"
task :up do
  [".", MOZART2, TRUFFLE, JVMCI, GRAAL].each { |dir|
    if File.directory?(dir)
      sh "cd #{dir} && git pull"
    end
  }
end
