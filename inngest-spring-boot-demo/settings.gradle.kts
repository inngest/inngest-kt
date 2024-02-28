

sourceControl {
    gitRepository(java.net.URI.create("https://github.com/inngest/inngest-kt.git")) {
        producesModule("com.inngest:inngest-spring-boot-adapter")
        rootDir = "inngest-spring-boot-adapter"
    }
}
