# earthshape/Makefile

# Edit this variable to point at your installation of JOGL.
# Then you then should be able to compile and run the program
# with "make" and "make run".
JOGAMP := d:/opt/jogamp-all-platforms/jar

# List of all the Java source files.
JAVA_FILES := $(shell find src -name '*.java')

# These are all of the JAR files that we need to compile and run.
# It lists the native code library for 64-bit Windows.  I suspect
# that means it will not run on other platforms without adjusting
# the library list, but I have not tested it.
JOGAMP_JARS := gluegen.jar
JOGAMP_JARS += gluegen-rt.jar
JOGAMP_JARS += joal.jar
JOGAMP_JARS += jocl.jar
JOGAMP_JARS += jogl-all.jar
JOGAMP_JARS += gluegen-rt-natives-windows-amd64.jar

# JAR files with their path.
JOGAMP_JAR_PATHS := $(foreach name,$(JOGAMP_JARS),$(JOGAMP)/$(name))

# Nonsense to deal with stupid 'make' syntax.
EMPTY :=
SPACE := $(EMPTY) $(EMPTY)

# JOGAMP_JARS collected together in classpath syntax.
JOGAMP_CP := $(subst $(SPACE),;,$(JOGAMP_JAR_PATHS))

# Although these are not needed to compile, they must be present
# in the same directory as the JOGL JARs in order to run.
JOGAMP_NATIVE_JARS :=
JOGAMP_NATIVE_JARS += gluegen-rt-natives-*.jar
JOGAMP_NATIVE_JARS += joal-natives-*.jar
JOGAMP_NATIVE_JARS += jocl-natives-*.jar
JOGAMP_NATIVE_JARS += jogl-all-natives-*.jar

JOGAMP_NATIVE_JAR_PATHS := $(foreach name,$(JOGAMP_NATIVE_JARS),$(JOGAMP)/$(name))

all: check-jogamp
	rm -rf bin
	mkdir -p bin/earthshape/textures
	$(MAKE) compile
	cp res/earthshape/*.png bin/earthshape/
	cp res/earthshape/textures/* bin/earthshape/textures/
	mkdir -p dist
	cd bin && jar cfm ../dist/earthshape.jar ../src/MANIFEST.MF *
	cp $(JOGAMP_JAR_PATHS) dist/
	cp $(JOGAMP_NATIVE_JAR_PATHS) dist/
	$(MAKE) dist/earthshape.sh dist/earthshape.bat
	@echo "Compilation complete.  Now run \"make run\"."

check-jogamp:
	@if [ ! -d '$(JOGAMP)' ]; then \
	  echo "Directory $(JOGAMP) does not exist."; \
	  echo "To compile and run this program, you need JOGL:"; \
	  echo "1. Download this file:"; \
	  echo "  https://jogamp.org/deployment/jogamp-current/archive/jogamp-all-platforms.7z"; \
	  echo "2. Unpack it using 7Zip:"; \
	  echo "  http://www.7-zip.org/download.html"; \
	  echo "3. Change the JARS variable in earthshape/Makefile to point"; \
	  echo "   at the \"jars\" directory of your JOGL installation."; \
	  echo "4. Run \"make\" and \"make run\" here."; \
	  exit 2; \
	fi
	@if [ ! -f '$(JOGAMP)/jogl-all.jar' ]; then \
	  echo "The file $(JOGAMP)/jogl-all.jar does not exist."; \
	  echo "In earthshape/Makefile, the JOGAMP variable must be"; \
	  echo "set to point at the 'jar' directory of the JOGL"; \
	  echo "installation, which must have jogl-all.jar inside it."; \
	  exit 2; \
	fi

compile:
	javac -sourcepath src -d bin \
	  -cp '$(JOGAMP_CP)' \
	  -Xmaxerrs 5 $(JAVA_FILES)
	@echo "^^ You can safely ignore the warning about RELEASE_6."

run:
	cd dist; java -cp '$(subst $(SPACE),;,$(JOGAMP_JARS));earthshape.jar' earthshape.EarthShape

dist/earthshape.sh: Makefile
	echo '#!/bin/sh' > $@
	echo 'java -cp "$(subst $(SPACE),;,$(JOGAMP_JARS));earthshape.jar" earthshape.EarthShape' >> $@
	chmod a+x $@

dist/earthshape.bat: Makefile
	echo 'java -cp $(subst $(SPACE),;,$(JOGAMP_JARS));earthshape.jar earthshape.EarthShape' > $@

release:
	@if [ "x$(VER)" = "x" ]; then \
	  echo "Must set VER variable to make a release."; \
	  exit 2; \
	fi
	mkdir -p tmp
	mkdir -p rel
	cp -R dist tmp/earthshape-$(VER)
	cd tmp ; zip -r ../rel/earthshape-$(VER).zip earthshape-$(VER)
	rm -rf 'tmp/earthshape-$(VER)'

clean:
	rm -rf bin dist tmp

# This only runs library unit tests.  There is not an automatic
# test of the main program.
check:
	java -cp bin -ea util.FloatUtil
	java -cp bin -ea util.Vector3d
	java -cp bin -ea util.Matrix3f
	java -cp bin -ea util.Matrix3d
	java -cp bin -ea earthshape.StarCatalog
	java -cp bin -ea earthshape.CurvatureCalculator

# EOF
