package io.quarkiverse.langchain4j.agentic.runtime.a2a;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;

import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.observability.A2AStreamingClientListener;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Configures the client of an A2A client agent with an {@link AgentAuthInterceptor}, before the client customizer of
 * the agent, if it has one, and adds the credentials to the request that fetches the agent's card, next to the agent's
 * own card headers.
 */
final class AuthenticatingA2AClientBuilder<T> implements A2AClientBuilder<T> {

    private final A2AClientBuilder<T> delegate;
    private final ClientCallInterceptor authInterceptor;
    private final Supplier<String> cardAuthorization;
    private final boolean cardHeadersSupported;
    private Consumer<ClientBuilder> clientCustomizer;
    private Supplier<Map<String, String>> cardHeaders;

    AuthenticatingA2AClientBuilder(A2AClientBuilder<T> delegate, ClientCallInterceptor authInterceptor,
            Supplier<String> cardAuthorization) {
        this.delegate = delegate;
        this.authInterceptor = authInterceptor;
        this.cardAuthorization = cardAuthorization;
        delegate.clientCustomizer(new AuthCustomizer());
        this.cardHeadersSupported = supportsCardHeaders(delegate);
    }

    // A builder that can't send card headers still gets the credentials on its A2A requests
    private boolean supportsCardHeaders(A2AClientBuilder<T> builder) {
        try {
            builder.agentCardHeaders(this::cardHeaders);
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    private Map<String, String> cardHeaders() {
        Map<String, String> own = cardHeaders == null ? null : cardHeaders.get();
        Map<String, String> headers = own == null ? new HashMap<>() : new HashMap<>(own);
        String authorization = cardAuthorization.get();
        if (authorization != null) {
            headers.put("Authorization", authorization);
        }
        return headers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public A2AClientBuilder<T> clientCustomizer(Consumer<?> clientCustomizer) {
        if (clientCustomizer != null) {
            this.clientCustomizer = (Consumer<ClientBuilder>) clientCustomizer;
        }
        return this;
    }

    @Override
    public A2AClientBuilder<T> name(String name) {
        delegate.name(name);
        return this;
    }

    @Override
    public A2AClientBuilder<T> description(String description) {
        delegate.description(description);
        return this;
    }

    @Override
    public A2AClientBuilder<T> tenant(String tenant) {
        delegate.tenant(tenant);
        return this;
    }

    @Override
    public A2AClientBuilder<T> agentCardHeaders(Supplier<Map<String, String>> headers) {
        if (!cardHeadersSupported) {
            delegate.agentCardHeaders(headers);
        }
        this.cardHeaders = headers;
        return this;
    }

    @Override
    public A2AClientBuilder<T> resolutionTimeout(Duration resolutionTimeout) {
        delegate.resolutionTimeout(resolutionTimeout);
        return this;
    }

    @Override
    public A2AClientBuilder<T> inputKeys(String... inputKeys) {
        delegate.inputKeys(inputKeys);
        return this;
    }

    @Override
    public A2AClientBuilder<T> outputKey(String outputKey) {
        delegate.outputKey(outputKey);
        return this;
    }

    @Override
    public A2AClientBuilder<T> async(boolean async) {
        delegate.async(async);
        return this;
    }

    @Override
    public A2AClientBuilder<T> listener(AgentListener agentListener) {
        delegate.listener(agentListener);
        return this;
    }

    @Override
    public A2AClientBuilder<T> activationCondition(String description, Predicate<AgenticScope> activationCondition) {
        delegate.activationCondition(description, activationCondition);
        return this;
    }

    @Override
    public A2AClientBuilder<T> streamingClientListener(A2AStreamingClientListener<?> clientListener) {
        delegate.streamingClientListener(clientListener);
        return this;
    }

    @Override
    public T build() {
        return delegate.build();
    }

    // Adds the interceptor, then runs the agent's own customizer, if it has one
    private final class AuthCustomizer implements Consumer<ClientBuilder> {

        @Override
        public void accept(ClientBuilder client) {
            client.withTransport(JSONRPCTransport.class,
                    new JSONRPCTransportConfigBuilder().addInterceptor(authInterceptor));
            if (clientCustomizer != null) {
                clientCustomizer.accept(client);
            }
        }
    }
}
