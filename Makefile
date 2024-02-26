TEST_ARGS=--console=rich --warning-mode=all

.PHONY: dev-ktor
dev-ktor:
	gradle inngest-test-server:run

.PHONY: dev-spring-boot
dev-spring-boot:
	gradle inngest-spring-boot-demo:bootRun

.PHONY: test
test: test-core test-ktor test-springboot-demo

.PHONY: itest
itest:
	gradle test $(TEST_ARGS) -p inngest-spring-boot-demo integrationTest

.PHONY: test-core
test-core:
	gradle test $(TEST_ARGS) -p inngest-core

.PHONY: test-ktor
test-ktor:
	gradle test $(TEST_ARGS) -p inngest-test-server

.PHONY: test-springboot-demo
test-springboot-demo:
	gradle test $(TEST_ARGS) -p inngest-spring-boot-demo

.PHONY: lint
lint:
	ktlint --color

.PHONY: fmt
fmt:
	ktlint -F

.PHONY: inngest-dev
inngest-dev:
	inngest-cli dev -v -u http://127.0.0.1:8080/api/inngest
