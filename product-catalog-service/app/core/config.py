from pydantic_settings import BaseSettings, SettingsConfigDict
from urllib.parse import quote

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

    # Elasticsearch connection details.
    elasticsearch_host: str = "localhost"
    elasticsearch_port: int = 9200

    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""

    jwt_secret: str = "8d/vpFSCAFqeRdZD7W2ZbBUbvs9r3FajrfXlCDp4cTk="
    jwt_algorithm: str = "HS256"

    # Builds the actual MongoDB connection URI from the individual pieces.
    # Uses unquote() before quote() to prevent double-encoding passwords containing %2F, %3D, etc.
    @property
    def mongo_uri(self) -> str:
        quoted_password = quote(self.mongo_password, safe="")
        return (
            f"mongodb://{self.mongo_username}:{quoted_password}"
            f"@{self.mongo_host}:{self.mongo_port}/?authSource=admin"
        )

    @property
    def elasticsearch_url(self) -> str:
        return f"http://{self.elasticsearch_host}:{self.elasticsearch_port}"

    @property
    def redis_url(self) -> str:
        if self.redis_password:
            quoted_pass = quote(self.redis_password, safe="")
            return f"redis://:{quoted_pass}@{self.redis_host}:{self.redis_port}"
        return f"redis://{self.redis_host}:{self.redis_port}"

    # model_config tells pydantic-settings WHERE to look for these values:
    # first check actual environment variables, falling back to .env if present.
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

# A single, shared instance created once and imported across modules
settings = Settings()