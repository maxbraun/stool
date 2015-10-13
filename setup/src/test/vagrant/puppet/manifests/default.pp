apt::source { 'backports':
  location          => 'http://http.debian.net/debian',
  release => "jessie-backports",
  repos   => 'main',
}

package { 'openjdk-8-jdk' :
  # require => Class['apt::source']
}