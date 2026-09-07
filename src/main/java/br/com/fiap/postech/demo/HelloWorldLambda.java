package br.com.fiap.postech.demo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.Map;

public class HelloWorldLambda implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {

        context.getLogger().log("Lambda Hello World executada!");

        return "Hello World!";
    }
}