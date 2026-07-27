from pydantic_settings import BaseSettings, SettingsConfigDict


# BaseSettings is Pydantic's dedicated class for loading configuration
# from environment variables, with full type validation — this is
# genuinely the Python equivalent of what @Value("${...}") +
# application.properties gave us in Spring Boot, except here it's one
# typed Python class instead of scattered string-keyed lookups.
class Settings(BaseSettings):
    mongo_host: str = "localhost"
    mongo_port: int = 27017
    mongo_username: str
    mongo_password: str
    mongo_db: str = "catalogdb"


     # NEW: Elasticsearch connection details. No username/password for
    # now, matching our local, security-disabled dev container — we'll
    # revisit this the same way we'll eventually revisit Mongo/Redis
    # auth for a genuine production deployment.
    elasticsearch_host: str = "localhost"
    elasticsearch_port: int = 9200

    redis_host: str = "localhost"
    redis_port: int = 6379

    # Builds the actual MongoDB connection URI from the individual
    # pieces above. Keeping this as a computed property (rather than
    # asking for a whole URI directly via env var) means each piece
    # stays independently readable/overridable, matching how we kept
    # DB_HOST/DB_PORT/etc. separate in User Service's
    # application.properties.
    @property
    def mongo_uri(self) -> str:
        return (
            f"mongodb://{self.mongo_username}:{self.mongo_password}"
            f"@{self.mongo_host}:{self.mongo_port}"
        )
    
    # NEW: assembled the same way as mongo_uri — one computed property,
    # rather than asking for a full URL directly via env var, so each
    # piece stays independently overridable.
    @property
    def elasticsearch_url(self) -> str:
        return f"http://{self.elasticsearch_host}:{self.elasticsearch_port}"

    

    @property
    def redis_url(self) -> str:
        return f"redis://{self.redis_host}:{self.redis_port}"


    # model_config tells pydantic-settings WHERE to look for these
    # values: first check actual environment variables (which is what
    # will be set inside the Docker container via docker-compose.yml),
    # falling back to a .env file if present (useful when running
    # locally outside Docker). This mirrors the ${MONGO_HOST:localhost}
    # style defaulting we used in Spring Boot's application.properties.
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


# A single, shared instance — created once when this module is first
# imported, then reused everywhere else in the app via import, rather
# than re-reading environment variables repeatedly.
settings = Settings()