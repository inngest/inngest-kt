.PHONY: dev
dev:
	gradle run inngest-test-server:run

.PHONY: test
test:
	gradle test inngest-core:test
