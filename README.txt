PLANC

1. Using etcd docker service

# docker run --rm --name myetcd -d -p 2379:2379 -p 2380:2380 -p 4001:4001 -p 7001:7001 elcolio/etcd:latest -name etcd0

2. Accessing etcd docker service

# docker exec -it myetcd /bin/sh

3. Defining routes in etcd service

# etcdctl mkdir /PLANC/virtualhosts/teste
# etcdctl mkdir /PLANC/virtualhosts/teste/path
# etcdctl mkdir /PLANC/virtualhosts/teste/path/$(echo '/' | base64)
# etcdctl set /PLANC/virtualhosts/teste/path/$(echo '/' | base64)/order 0
# etcdctl set /PLANC/virtualhosts/teste/path/$(echo '/' | base64)/target 0
# etcdctl set /PLANC/virtualhosts/teste/path/$(echo '/' | base64)/type PATH
# etcdctl mkdir /PLANC/pools
# etcdctl mkdir /PLANC/pools/0
# etcdctl set /PLANC/pools/0/loadbalance ROUNDROBIN
# etcdctl set /PLANC/pools/0/0 http://127.0.0.1:8080

4. Resetting all

# etcdctl set /PLANC/reset_all 0 --ttl 5

5. Resetting only one virtualhost

# etcdctl set /PLANC/reset/teste teste
