package com.alibaba.cloud.spring.boot.fc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.AbstractSpringFunctionAdapterInitializer;
import org.springframework.messaging.Message;

import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.Credentials;
import com.aliyun.fc.runtime.FunctionComputeLogger;
import com.aliyun.fc.runtime.FunctionParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author <a href="mailto:chenxilzx1@gmail.com">theonefx</a>
 */
public abstract class AbstractAliyunFCInvoker extends AbstractSpringFunctionAdapterInitializer<Context> {

    private final static ExecutionContextDelegate EXECUTION_CTX_DELEGATE = new ExecutionContextDelegate();

    @Autowired(required = false)
    protected ObjectMapper mapper;

    public AbstractAliyunFCInvoker() {
        super();
        preInit();
    }

    public AbstractAliyunFCInvoker(Class<?> configurationClass) {
        super(configurationClass);
        preInit();
    }

    private void preInit() {
        System.setProperty("spring.http.converters.preferred-json-mapper", "gson");
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        this.initialize(null);
    }

    @Override
    public final void initialize(Context context) {
        if (EXECUTION_CTX_DELEGATE.target == null) {
            synchronized (EXECUTION_CTX_DELEGATE) {
                if (EXECUTION_CTX_DELEGATE.target == null) {
                    EXECUTION_CTX_DELEGATE.target = context;
                }
            }
            if (System.getenv().containsKey("SCF_FUNC_NAME")) {
                String functionName = System.getenv("SCF_FUNC_NAME");
                System.setProperty("function.name", functionName);
            }
        }
        super.initialize(EXECUTION_CTX_DELEGATE);
        if (this.mapper == null) {
            this.mapper = new ObjectMapper();
        }
    }

    protected Object doInvoke(Object param) {
        // TODO convert

        Publisher<?> input = param == null ? Mono.just("null") : extract(param);

        Publisher<?> output = apply(input);
        Object realInput = param;
        if (param instanceof Pair) {
            realInput = ((Pair<?, ?>) param).getKey();
        }
        Object result = result(realInput, output);
        return result;
    }

    protected boolean functionReturnsMessage(Object output) {
        return output instanceof Message;
    }


    protected String serializeBody(Object body) {
        try {
            if (body instanceof CharSequence) {
                return String.valueOf(body);
            }
            return this.mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot convert output", e);
        }
    }

    protected byte[] serializeResult(Object body) {
        if (body instanceof byte[]) {
            return (byte[]) body;
        } else if (body instanceof String) {
            String seq = (String) body;
            return seq.getBytes();
        }
        try {
            return this.mapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot convert output", e);
        }
    }

    protected Flux<?> extract(Object input) {
        return Flux.just(input);
    }

    protected static class ExecutionContextDelegate implements Context {

        private Context target;

        @Override
        public String getRequestId() {
            return target.getRequestId();
        }

        @Override
        public Credentials getExecutionCredentials() {
            return target.getExecutionCredentials();
        }

        @Override
        public FunctionParam getFunctionParam() {
            return target.getFunctionParam();
        }

        @Override
        public FunctionComputeLogger getLogger() {
            return target.getLogger();
        }
    }
}
