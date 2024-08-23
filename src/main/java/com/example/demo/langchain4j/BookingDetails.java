package com.example.demo.langchain4j;



import java.time.LocalDate;

public record BookingDetails(String bookingNumber,
                             String firstName,
                             String lastName,
                             LocalDate date,
                             BookingStatus bookingStatus,
                             String from,
                             String to,
                             String bookingClass) {
}