.DEFAULT_GOAL := help
# Run `mvn wrapper:wrapper` once locally to pin the Maven version, then switch this to ./mvnw
MVN := mvn

.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

.PHONY: dev-up
dev-up: ## Start Postgres, Redis, Temporal, Ollama and Jaeger
	docker compose up -d
	@echo "Temporal UI  http://localhost:8233"
	@echo "Jaeger       http://localhost:16686"

.PHONY: dev-down
dev-down: ## Stop the development stack (keeps volumes)
	docker compose down

.PHONY: dev-reset
dev-reset: ## Stop the stack and delete all volumes
	docker compose down -v

.PHONY: build
build: ## Compile and run the full test suite
	$(MVN) clean verify

.PHONY: arch
arch: ## Run only the module boundary tests - the fast feedback loop
	$(MVN) -pl relay-app test -Dtest='ModularityTests,ArchitectureRulesTest'

.PHONY: docs
docs: arch ## Regenerate the module canvas and PlantUML diagrams
	@echo "Diagrams written to relay-app/target/spring-modulith-docs/"

.PHONY: mutation
mutation: ## Run PIT mutation testing (slow; the E4 rung of the evidence ladder)
	$(MVN) -P mutation test

.PHONY: run
run: ## Run the application against the local stack
	$(MVN) -pl relay-app spring-boot:run
