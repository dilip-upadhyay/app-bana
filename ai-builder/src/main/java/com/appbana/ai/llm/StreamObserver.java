package com.appbana.ai.llm;

/**
 * Observer interface for streaming LLM responses
 * Receives tokens as they arrive from the LLM
 */
public interface StreamObserver<T> {
    /**
     * Called when a new token arrives
     */
    void onNext(T token);

    /**
     * Called when the stream completes successfully
     */
    void onComplete();

    /**
     * Called when an error occurs during streaming
     */
    void onError(Throwable error);
}
