# VFS FTP Evaluation Protocol

Status: DRAFT

## Scope
Evaluation only. No production switch from CommonsNetFtpFileService to VFS.

## Environment
- Host: ____________________
- User: ____________________
- Protocol: FTP / FTPS
- Date/Time: _______________
- Tester: __________________

## Functional Regression Checks
- [ ] MVS listing: dataset list
- [ ] MVS listing: PDS list
- [ ] MVS listing: member list (PDS(member))
- [ ] Read member (record/padding/encoding settings respected)
- [ ] Write member (record/padding/encoding settings respected)
- [ ] Path semantics: bookmark/deep link open without CWD
- [ ] Error: wrong password → AUTH_FAILED mapping
- [ ] Error: not found
- [ ] Error: permission denied

## Performance / Connection Behavior
- [ ] Connection reuse / no excessive connections
- [ ] Parallel operations (two lists in quick succession) no deadlock/race

## Evidence Notes
- Listing evidence: ________________________________
- Read/write evidence: _____________________________
- Error mapping evidence: __________________________
- Connection reuse evidence: _______________________

## Go / No-Go
- [ ] GO
- [ ] NO-GO

## Feature Gaps
1. ____________________
2. ____________________
3. ____________________

## Follow-up Tickets
- [ ] Issue: ____________________
- [ ] Issue: ____________________
- [ ] Issue: ____________________

