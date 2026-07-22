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