package com.proj.ckitchens.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proj.ckitchens.model.Order;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.List;

public class OrderParser {

    public static List<Order> readFromFile(String fileName) {
        ObjectMapper mapper = new ObjectMapper();
        List<Order> orders = null;
        try {
            orders = Arrays.asList(mapper.readValue(new ClassPathResource(fileName).getFile(), Order[].class));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("can not read from file " + fileName + " from default location");
        }
        return orders;
    }

}
