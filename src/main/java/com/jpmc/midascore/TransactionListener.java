package com.jpmc.midascore;

import com.jpmc.midascore.foundation.Transaction;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.jpmc.midascore.entity.*;
import com.jpmc.midascore.repository.*;
import com.jpmc.midascore.foundation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@Component
public class TransactionListener {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionRecordRepository transactionRecordRepository;
    @Autowired
    private RestTemplate restTemplate;

    @KafkaListener(topics = "${general.kafka-topic}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handleTransaction(Transaction transaction) {
        System.out.println("Received details : " + transaction.getSenderId() + " " + transaction.getRecipientId() + " " + transaction.getAmount());
        if(isValidTransaction(transaction)){
            processTransaction(transaction);
            System.out.println("Transaction processed successfully.:"+transaction);
        }else{
            System.out.println("Transaction failed.:"+transaction);
        }
    }

    private boolean isValidTransaction(Transaction transaction){
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());
        if(sender == null || recipient == null){
            System.out.println("Invalid sender or recipient.");
            return false;
        }
        if(sender.getBalance() < transaction.getAmount()){
            System.out.println("Insufficient balance.");
            return false;
        }
        return true;
    }

    private void processTransaction(Transaction transaction){
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());

        Incentive incentive = getIncentive(transaction);
        double incentiveAmount = incentive != null ? incentive.getAmount() != null ? incentive.getAmount().doubleValue() : 0.0 : 0.0;

        System.out.println("Incentive amount: " + incentiveAmount);


        sender.setBalance(sender.getBalance() - transaction.getAmount());
        recipient.setBalance((float)(recipient.getBalance() + transaction.getAmount() + incentiveAmount));

        userRepository.save(sender);
        userRepository.save(recipient);

        TransactionRecord t_record = new TransactionRecord(sender,recipient,transaction.getAmount());
        transactionRecordRepository.save(t_record);
        System.out.println("Transaction record saved: \n" + sender.getName() + " -> " + recipient.getName() + " : " + transaction.getAmount() + "\n" + sender.getBalance() + " " + recipient.getBalance() + "\n" + "Incentive: " + incentiveAmount);
    }

    private Incentive getIncentive(Transaction transaction){
        try {
            String url = "http://localhost:8080/incentive" + transaction.getAmount();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Transaction> request = new HttpEntity<>(transaction, headers);

            Incentive incentive = restTemplate.postForObject(url, request, Incentive.class);

            System.out.println("Incentive received: " + (incentive != null ? incentive.getAmount() : "null"));
            return incentive;

        } catch (Exception e) {
            System.out.println("Error fetching incentive: " + e.getMessage());
            return null;
        }
    }

    public void listen(Transaction transaction) {
        System.out.println("Received transaction: " + transaction);
    }
}