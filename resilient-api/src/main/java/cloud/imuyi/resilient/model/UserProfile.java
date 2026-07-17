package cloud.imuyi.resilient.model;

/**
 * 用户画像模型
 */
public class UserProfile {

    private Long id;
    private String name;
    private String email;

    public UserProfile() {
    }

    public UserProfile(Long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}