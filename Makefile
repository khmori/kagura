.PHONY: run test build compile clean

run:
	cd backend && ./mvnw spring-boot:run

test:
	cd backend && ./mvnw test

test-one:
	cd backend && ./mvnw test -Dtest=$(CLASS)

build:
	cd backend && ./mvnw clean package -DskipTests

compile:
	cd backend && ./mvnw clean compile

clean:
	cd backend && ./mvnw clean
