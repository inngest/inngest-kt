.PHONY: dev
dev:
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
