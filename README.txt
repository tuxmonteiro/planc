PLANC

1. Defining routes in etcd service

# etcdctl mkdir /PLANC/virtualhosts/teste
# etcdctl mkdir /PLANC/virtualhosts/teste/path
# etcdctl mkdir /PLANC/virtualhosts/teste/path/$(echo '/' | base64)
# etcdctl set /PLANC/virtualhosts/teste/path/$(echo '/' | base64)/order 0
# etcdctl set /PLANC/virtualhosts/teste/path/$(echo '/' | base64)/target 0
# etcdctl mkdir /PLANC/pools
# etcdctl mkdir /PLANC/pools/0
# etcdctl set /PLANC/pools/0/0 http://127.0.0.1:8080

2. Resetting all

# etcdctl set /PLANC/reset_all 0

3. Disable "resetting all"

# etcdctl rm /PLANC/reset_all 0
