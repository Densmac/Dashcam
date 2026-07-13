# Cleartext policy

The YantopCam-compatible dashcam exposes only local HTTP control endpoints on
`192.168.169.1`. The app enables cleartext traffic solely so those local camera
requests can work while connected to the `DASHCAM` Wi-Fi network.

There is no backend, analytics service, cloud sync, remote config, or internet
host dependency. Repository and download code must continue using
`DashcamConstants.HTTP_BASE_URL` or direct dashcam file paths derived from the
same host.
