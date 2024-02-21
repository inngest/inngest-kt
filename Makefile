.PHONY: dev
dev:
	gradle run inngest-test-server:run

.PHONY: test
test:
	gradle test inngest-core:test

.PHONY: lint
lint:
	ktlint --color
