package com.example.demo.langchain4j;


import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

    @Component
    public class LangChainTools {

        @Autowired
        private  FlightService service;



        @Tool("this tool provide booking details")
        public BookingDetails getBookingDetails(String bookingNumber, String firstName, String lastName) {
            return service.getBookingDetails(bookingNumber, firstName, lastName);
        }

        @Tool
        public void changeBooking(String bookingNumber, String firstName, String lastName, String date, String from, String to) {
            service.changeBooking(bookingNumber, firstName, lastName, date, from, to);
        }

        @Tool
        public void cancelBooking(String bookingNumber, String firstName, String lastName) {
            service.cancelBooking(bookingNumber, firstName, lastName);
        }

    }