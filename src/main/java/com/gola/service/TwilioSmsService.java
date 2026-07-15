package com.gola.service;

import com.gola.config.GolaProperties;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwilioSmsService {
    private final GolaProperties properties;

    public void sendSms(String to, String body) {
        var twilio = properties.getTwilio();
        if (to == null || to.isBlank()) return;
        if (twilio.getAccountSid() == null || twilio.getAccountSid().isBlank()
                || twilio.getAuthToken() == null || twilio.getAuthToken().isBlank()
                || twilio.getFromNumber() == null || twilio.getFromNumber().isBlank()) {
            log.debug("Twilio SMS skipped because credentials/from number are not configured");
            return;
        }

        try {
            Twilio.init(twilio.getAccountSid(), twilio.getAuthToken());
            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(twilio.getFromNumber()),
                    body
            ).create();
            log.info("Twilio SMS sent to {} with sid {}", to, message.getSid());
        } catch (Exception e) {
            log.warn("Twilio SMS failed for {}: {}", to, e.getMessage());
        }
    }
}
