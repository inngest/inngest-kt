package com.inngest

class Inngest {
    constructor(
        app_id: String,
    ) {
        // TODO - Fetch INNGEST_EVENT_KEY env variable
    }

//    fun send(event: Event): EventAPIResponse {
//        val requestBody = Klaxon().toJsonString(event)
//        val client = HttpClient.newBuilder().build()
//        val request =
//            HttpRequest.newBuilder()
//                .uri(URI.create("http://localhost:8288/e/"))
//                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
//                .build()
//        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
//        val body = Klaxon().parse<EventAPIResponse>(response)
//        return body;
//    }
}
