package com.bhoomi.paycore.ledger;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paycore.tigerbeetle")
public record TigerBeetleProperties(long clusterId, List<String> addresses) {
}