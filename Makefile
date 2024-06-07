

all: compile

clean: ; sbt clean && rm -rf target

compile: ; sbt nativeCompile

install: ; cp ./target/cloud /usr/bin