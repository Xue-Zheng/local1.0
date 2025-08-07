package nz.etu.voting.service;
import nz.etu.voting.domain.dto.request.AdminLoginRequest;
import nz.etu.voting.domain.dto.response.AdminLoginResponse;
public interface AuthService {
    AdminLoginResponse adminLogin(AdminLoginRequest request);
}