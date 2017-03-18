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
JARS := $(JOGAMP)/gluegen.jar;$(JOGAMP)/gluegen-rt.jar;$(JOGAMP)/joal.jar;$(JOGAMP)/jocl.jar;$(JOGAMP)/jogl-all.jar;$(JOGAMP)/gluegen-rt-natives-windows-amd64.jar

all: check-jogamp
	rm -rf bin
	mkdir -p bin/earthshape/textures
	$(MAKE) compile
	cp res/earthshape/*.png bin/earthshape/
	cp res/earthshape/textures/* bin/earthshape/textures/
	mkdir -p dist
	cd bin && jar cfm ../dist/earthshape.jar ../src/MANIFEST.MF *
	@echo "Compilation complete.  Now run \"make run\"."

check-jogamp:
	@if [ ! -d '$(JOGAMP)' ]; then \
	  echo "Directory $(JOGAMP) does not exit."; \
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

compile:
	javac -sourcepath src -d bin \
	  -cp '$(JARS)' \
	  -Xmaxerrs 5 $(JAVA_FILES)
	@echo "^^ You can safely ignore the warning about RELEASE_6."

run:
	java -cp '$(JARS);dist/earthshape.jar' earthshape.EarthShape

clean:
	rm -rf bin dist

# This only runs library unit tests.  There is not an automatic
# test of the main program.
check:
	java -cp bin -ea util.FloatUtil
	java -cp bin -ea util.Vector3d
	java -cp bin -ea earthshape.StarCatalog

# EOF
