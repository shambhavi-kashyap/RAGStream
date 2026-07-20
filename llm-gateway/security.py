from fastapi import HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt
import os

SECRET_KEY = os.getenv("JWT_SECRET")
ALGORITHMS = ["HS256", "HS384", "HS512"]

security_scheme = HTTPBearer()

def verify_jwt(credentials: HTTPAuthorizationCredentials = Security(security_scheme)):
    try:
        payload = jwt.decode(credentials.credentials, SECRET_KEY, algorithms=ALGORITHMS)
        
        tenant_id = payload.get("tenant_id")
        if not tenant_id:
            raise HTTPException(status_code=403, detail="Invalid token: Missing tenant context")
            
        return payload
    except Exception as e:
        raise HTTPException(status_code=401, detail=f"Invalid authentication token: {str(e)}")