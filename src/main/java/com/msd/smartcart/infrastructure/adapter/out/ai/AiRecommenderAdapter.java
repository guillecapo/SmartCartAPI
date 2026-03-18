package com.msd.smartcart.infrastructure.adapter.out.ai;

import com.msd.smartcart.domain.port.out.AiRecommenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AiRecommenderAdapter implements AiRecommenderPort {

    @Override
    public void suggest(String userId, List<String> productIds) {
        log.info("AI recommendations requested [userId={}, productIds={}]", userId, productIds);

        // STUB — integración con servicio de IA pendiente
        // Cuando se implemente:
        // 1. Llamar al endpoint de recomendaciones con userId + productIds
        // 2. Persistir o publicar las sugerencias para que el cliente las consuma
        // 3. Aplicar timeout — si la IA tarda más de X ms, el caller ya continuó (best-effort)
        // 4. El adapter debe traducir cualquier excepción a InfrastructureException
        //    para mantener el contrato con el dominio

        log.debug("AI stub — no action taken [userId={}]", userId);
    }
}
