TEST_ARGS=--console=rich --warning-mode=all
GRADLE=./gradlew
SPRING_BOOT_VERSION?=2.7.18
SPRING_BOOT_ARGS=-PspringBootVersion=$(SPRING_BOOT_VERSION)

.PHONY: dev-ktor
dev-ktor:
	$(GRADLE) inngest-test-server:run

.PHONY: dev-spring-boot
dev-spring-boot:
	$(GRADLE) $(SPRING_BOOT_ARGS) inngest-spring-boot-demo:bootRun

.PHONY: test
test: test-core test-ktor test-springboot-adapter test-springboot-demo

.PHONY: itest
itest:
	$(GRADLE) test $(TEST_ARGS) $(SPRING_BOOT_ARGS) -p inngest-spring-boot-demo integrationTest

.PHONY: test-core
test-core:
	$(GRADLE) test $(TEST_ARGS) -p inngest

.PHONY: test-ktor
test-ktor:
	$(GRADLE) test $(TEST_ARGS) -p inngest-test-server

.PHONY: test-springboot-demo
test-springboot-demo:
	$(GRADLE) test $(TEST_ARGS) $(SPRING_BOOT_ARGS) -p inngest-spring-boot-demo

.PHONY: test-springboot-adapter
test-springboot-adapter:
	$(GRADLE) test $(TEST_ARGS) $(SPRING_BOOT_ARGS) -p inngest-spring-boot-adapter

.PHONY: lint
lint:
	ktlint --color

.PHONY: fmt
fmt:
	ktlint -F

.PHONY: inngest-dev
inngest-dev:
	inngest dev -v -u http://127.0.0.1:8080/api/inngest
