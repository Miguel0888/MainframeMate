@echo off
cd C:\Projects\MainframeMate
call gradlew.bat :app:compileJava > C:\Projects\MainframeMate\tmp_build_out.txt 2>&1
echo EXIT:%ERRORLEVEL% >> C:\Projects\MainframeMate\tmp_build_out.txt

