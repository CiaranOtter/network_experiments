JC = javac 
DEST = ./build
FILES = $(subst .java,.class, $(wildcard *.java))

all: $(FILES)
	@echo $(FILES)


%.class:
	$(JC)  $*.java


clean:
