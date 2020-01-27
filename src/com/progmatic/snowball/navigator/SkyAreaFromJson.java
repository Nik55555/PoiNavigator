package com.progmatic.snowball.navigator;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class SkyAreaFromJson {
    public static void main(String[] args) {
        String jsonStr = "{\"name\" : \"Abcd\", \"greeting\": \"Hello\" }"; //Sample Json String

        Gson gson = new Gson(); // Creates new instance of Gson
        JsonElement element = gson.fromJson (jsonStr, JsonElement.class); //Converts the json string to JsonElement without POJO
        JsonObject jsonObj = element.getAsJsonObject(); //Converting JsonElement to JsonObject

        String name = jsonObj.get("name").getAsString(); //To fetch the values from json object
        String greeting = jsonObj.get("greeting").getAsString();
    }
}
