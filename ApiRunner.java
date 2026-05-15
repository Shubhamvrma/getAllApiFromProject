package com.amnex.entity;

import com.amnex.entity.ApiMaster;
import com.amnex.entity.ApiRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ApiRunner implements CommandLineRunner {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ApiRepository apiRepository;

    @Override
    public void run(String... args) {

        Map<RequestMappingInfo, HandlerMethod> map =
                handlerMapping.getHandlerMethods();

        List<ApiMaster> apiList = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : map.entrySet()) {

            RequestMappingInfo info = entry.getKey();

            HandlerMethod handlerMethod = entry.getValue();

            Set<String> patterns = info.getPatternValues();

            Set<RequestMethod> methods =
                    info.getMethodsCondition().getMethods();

            for (String pattern : patterns) {

                // Ignore swagger/system APIs
                if (shouldSkip(pattern)) {
                    continue;
                }

                if (methods.isEmpty()) {

                    ApiMaster api = buildApi(
                            pattern,
                            "ALL",
                            handlerMethod
                    );

                    apiList.add(api);

                } else {

                    for (RequestMethod method : methods) {

                        ApiMaster api = buildApi(
                                pattern,
                                method.name(),
                                handlerMethod
                        );

                        apiList.add(api);
                    }
                }
            }
        }

        saveApis(apiList);

        System.out.println("Total APIs Discovered : " + apiList.size());
    }

    private ApiMaster buildApi(
            String pattern,
            String method,
            HandlerMethod handlerMethod
    ) {

        ApiMaster api = new ApiMaster();

        api.setApiPattern(pattern);

        api.setHttpMethod(method);

        api.setControllerName(
                handlerMethod.getBeanType().getSimpleName()
        );

        api.setMethodName(
                handlerMethod.getMethod().getName()
        );

        api.setModuleName(
                extractModule(pattern)
        );

        api.setIsActive(true);

        return api;
    }

    private String extractModule(String pattern) {

        String[] parts = pattern.split("/");

        if (parts.length > 1
                && parts[1] != null
                && !parts[1].trim().isEmpty()) {

            return parts[1].toUpperCase();
        }

        return "COMMON";
    }

    private boolean shouldSkip(String pattern) {

        return pattern.contains("/swagger")
                || pattern.contains("/v3/api-docs")
                || pattern.contains("/actuator")
                || pattern.contains("/error")
                || pattern.contains("/favicon");
    }

    private void saveApis(List<ApiMaster> apis) {

        List<ApiMaster> existingApis =
                apiRepository.findAll();

        Set<String> existingKeys = existingApis.stream()
                .map(a ->
                        a.getApiPattern()
                                + "_"
                                + a.getHttpMethod()
                )
                .collect(Collectors.toSet());

        List<ApiMaster> newApis = apis.stream()
                .filter(a ->
                        !existingKeys.contains(
                                a.getApiPattern()
                                        + "_"
                                        + a.getHttpMethod()
                        )
                )
                .collect(Collectors.toList());

        if (!newApis.isEmpty()) {

            apiRepository.saveAll(newApis);

            System.out.println(
                    "New APIs Saved : " + newApis.size()
            );

        } else {

            System.out.println(
                    "No New APIs Found"
            );
        }
    }
}
