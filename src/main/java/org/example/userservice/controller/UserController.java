package org.example.userservice.controller;

import org.example.userservice.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Map<Long, User> users = new HashMap<>();

    static {
        users.put(1L, new User(1L, "John Doe", "john@example.com"));
        users.put(2L, new User(2L, "Jane Smith", "jane@example.com"));
        users.put(3L, new User(3L, "Mike Brown", "mike@example.com"));
        users.put(4L, new User(4L, "Emily Davis", "emily@example.com"));
        users.put(5L, new User(5L, "Chris Wilson", "chris@example.com"));
        users.put(6L, new User(6L, "Sarah Taylor", "sarah@example.com"));
        users.put(7L, new User(7L, "David Anderson", "david@example.com"));
        users.put(8L, new User(8L, "Laura Thomas", "laura@example.com"));
        users.put(9L, new User(9L, "Daniel Moore", "daniel@example.com"));
        users.put(10L, new User(10L, "Sophia Martin", "sophia@example.com"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        User user = users.get(id);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(user);
    }
}
