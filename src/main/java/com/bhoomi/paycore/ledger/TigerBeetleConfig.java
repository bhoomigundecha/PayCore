package com.bhoomi.paycore.ledger;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tigerbeetle.Client;
import com.tigerbeetle.UInt128;

@Configuration
@EnableConfigurationProperties(TigerBeetleProperties.class)
public class TigerBeetleConfig {

    @Bean(destroyMethod = "close")
    public Client tigerBeetleClient(TigerBeetleProperties props) {
        byte[] clusterId = UInt128.asBytes(props.clusterId());
        String[] addresses = props.addresses().toArray(String[]::new);
        return new Client(clusterId, addresses);
    }
}