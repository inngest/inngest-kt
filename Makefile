.PHONY: dev-ktor
dev-ktor:
	gradle inngest-test-server:run

.PHONY: dev-spring-boot
dev-spring-boot:
	gradle inngest-spring-boot-demo:bootRun

.PHONY: test
test:
	gradle test inngest-core:test
	gradle inngest-spring-boot-demo:test

.PHONY: lint
lint:
	ktlint --color

.PHONY: fmt
fmt:
	ktlint -F

.PHONY: inngest-dev
inngest-dev:
	inngest-cli dev -v -u http://127.0.0.1:8080/api/inngest
