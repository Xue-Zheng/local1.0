package nz.etu.voting.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ===== 公开访问端点 =====
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/api/registration/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/checkin/**").permitAll() // QR码扫描确认出席
                        .requestMatchers("/api/event-registration/**").permitAll() // EventMember注册
                        .requestMatchers("/api/venue/checkin/**").permitAll() // BMM场馆签到扫码

                        // ===== BMM系统公开端点 =====
                        .requestMatchers("/api/bmm/**").permitAll() // BMM前端功能（包括preferences提交）
                        .requestMatchers("/api/admin/ticket-emails/bmm-ticket/**").permitAll() // BMM票据访问
                        .requestMatchers("/api/admin/ticket-emails/bmm-checkin").permitAll() // BMM签到

                        // ===== 需要公开访问的管理端点 =====
                        .requestMatchers("/api/admin/events/upcoming").permitAll()
                        .requestMatchers("/api/admin/sync/**").permitAll()
                        .requestMatchers("/api/admin/events").permitAll()
                        .requestMatchers("/api/admin/events/**").permitAll() // 事件详情和管理
                        .requestMatchers("/api/admin/members/stats").permitAll()
                        .requestMatchers("/api/admin/templates/**").permitAll()
                        .requestMatchers("/api/admin/checkin/**").permitAll()
                        .requestMatchers("/api/admin/registration/**").permitAll() // 注册管理所有端点
                        .requestMatchers("/api/admin/email/preview").permitAll() // Preview email recipients
                        .requestMatchers("/api/admin/email/preview-by-criteria").permitAll() // Preview email by criteria
                        .requestMatchers("/api/admin/sms/preview").permitAll() // Preview SMS recipients
                        .requestMatchers("/api/admin/sms/preview-by-criteria").permitAll() // Preview SMS by criteria
                        .requestMatchers("/api/admin/data-categories/**").permitAll() // Data categories and statistics
                        .requestMatchers("/api/admin/reports/**").permitAll() // 报告和统计
                        .requestMatchers("/api/admin/settings/**").permitAll() // 系统设置
                        .requestMatchers("/api/admin/import/**").permitAll() // 数据导入
                        .requestMatchers("/api/admin/notifications/**").permitAll() // 通知管理
                        .requestMatchers("/api/admin/smart-notifications/**").permitAll() // 智能通知
                        .requestMatchers("/api/admin/event-templates/**").permitAll() // 事件模板
                        .requestMatchers("/api/admin/member-management/**").permitAll() // 会员管理
                        .requestMatchers("/api/admin/quick-search").permitAll() // 快速搜索
                        .requestMatchers("/api/admin/**").permitAll() // 管理员基础统计和健康检查

                        // ===== 严格保护的管理员端点 =====
                        .requestMatchers("/api/admin/migration/**").hasRole("ADMIN") // 迁移功能需要管理员权限
                        .requestMatchers("/api/admin/ticket-emails/send/**").hasRole("ADMIN") // 批量发送票据需要管理员权限
                        .requestMatchers("/api/admin/email/send").hasRole("ADMIN") // 发送邮件需要管理员权限
                        .requestMatchers("/api/admin/sms/send").hasRole("ADMIN") // 发送短信需要管理员权限
                        .requestMatchers("/api/admin/quick-send-email").hasRole("ADMIN") // 快速发送邮件需要管理员权限
                        .requestMatchers("/api/admin/quick-send-sms").hasRole("ADMIN") // 快速发送短信需要管理员权限

                        // ===== 其他API需要认证 =====
                        .requestMatchers("/api/**").authenticated()

                        // ===== 前端页面允许访问（由前端路由控制权限）=====
                        .anyRequest().permitAll()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}