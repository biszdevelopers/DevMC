/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.players.web;

import dev.bisz.players.web.AuthenticationDecision;
import dev.bisz.players.web.LoginAttempt;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface AuthenticationGateway {
  public CompletionStage<AuthenticationDecision> verify(LoginAttempt var1);
}
