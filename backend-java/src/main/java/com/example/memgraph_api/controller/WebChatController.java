package com.example.memgraph_api.controller;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class WebChatController {

    private final ChatClient chatClient;

    @Autowired
    public WebChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askWeb(@RequestBody Map<String, String> payload) {
        String question = payload.get("question");
        
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("answer", "Te rog să pui o întrebare validă."));
        }

        try {
            String systemPrompt = "Ești un asistent medical AI pentru pacienți. "
                                + "Rolul tău este să oferi informații generale despre analize medicale, simptome, valori crescute/scăzute etc. "
                                + "Explică pe înțelesul tuturor, clar și prietenos. La final, adaugă un disclaimer recomandând consultul unui medic. "
                                + "Fii scurt și la obiect. "
                                + "REGULĂ DE LIMBĂ: Răspunde OBLIGATORIU în aceeași limbă în care a fost pusă întrebarea (ex. română, engleză etc.).";

            String aiResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(question)
                    .call()
                    .content();

            return ResponseEntity.ok(Map.of("answer", aiResponse));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("answer", "Eroare la contactarea asistentului AI: " + e.getMessage()));
        }
    }
}