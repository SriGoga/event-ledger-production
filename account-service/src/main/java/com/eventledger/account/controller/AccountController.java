package com.eventledger.account.controller;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/accounts")
public class AccountController {
 @GetMapping("/{accountId}/balance")
 public String balance(@PathVariable String accountId){
   return "0.00";
 }
}