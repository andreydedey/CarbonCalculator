package com.example.carboncalculator.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.ChangeRoleRequest;
import com.example.carboncalculator.dto.InviteRequest;
import com.example.carboncalculator.dto.UserMemberDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.services.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('GESTOR')")
    public List<UserMemberDTO> list() {
        return userService.listMembers();
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<UserMemberDTO> invite(@RequestBody InviteRequest request,
                                                 @AuthenticationPrincipal AppUser user) {
        UserMemberDTO member = userService.invite(request.email(), request.role(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('GESTOR')")
    public UserMemberDTO changeRole(@PathVariable UUID id,
                                     @RequestBody ChangeRoleRequest request,
                                     @AuthenticationPrincipal AppUser user) {
        return userService.changeRole(id, request.role(), user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<Void> revoke(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUser user) {
        userService.revoke(id, user);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(UserService.DuplicateInviteException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateInvite(UserService.DuplicateInviteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(UserService.MemberNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMemberNotFound(UserService.MemberNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(UserService.CannotModifySelfException.class)
    public ResponseEntity<Map<String, String>> handleCannotModifySelf(UserService.CannotModifySelfException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(UserService.LastGestorException.class)
    public ResponseEntity<Map<String, String>> handleLastGestor(UserService.LastGestorException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(UserService.InvalidRoleException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRole(UserService.InvalidRoleException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }
}
